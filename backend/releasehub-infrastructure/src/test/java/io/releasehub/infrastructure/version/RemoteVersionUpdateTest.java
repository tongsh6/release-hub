package io.releasehub.infrastructure.version;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdaterPort;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.version.BuildTool;
import io.releasehub.infrastructure.gitlab.InMemoryGitLabFileAdapter;
import io.releasehub.infrastructure.gitlab.RealGitLabFileAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** 对真实文件适配器使用进程内 HTTP fixture，覆盖业务读取到条件写入的完整链路。 */
class RemoteVersionUpdateTest {
    private static final String REPO = "https://gitlab.invalid/acme/repo.git";
    private static final String ORIGINAL_REVISION = "a".repeat(40);

    @ParameterizedTest
    @EnumSource(value = BuildTool.class, names = {"MAVEN", "GRADLE"})
    void shouldWriteUsingRevisionOfTheContentThatWasRead(BuildTool buildTool) throws Exception {
        verifyRemoteUpdate(buildTool, false);
    }

    @ParameterizedTest
    @EnumSource(value = BuildTool.class, names = {"MAVEN", "GRADLE"})
    void shouldPreserveExternalChangeBetweenReadAndWrite(BuildTool buildTool) throws Exception {
        verifyRemoteUpdate(buildTool, true);
    }

    private void verifyRemoteUpdate(BuildTool buildTool, boolean concurrentChange) throws Exception {
        var mapper = new ObjectMapper();
        var settings = mock(SettingsPort.class);
        when(settings.getGitLab()).thenReturn(Optional.of(new SettingsPort.SettingsGitLab("https://gitlab.invalid", "fixture")));
        var files = new RealGitLabFileAdapter(settings);
        var http = (RestTemplate) ReflectionTestUtils.getField(files, "restTemplate");
        assertNotNull(http);
        var server = MockRestServiceServer.bindTo(http).build();
        String path = buildTool == BuildTool.MAVEN ? "pom.xml" : "gradle.properties";
        String original = buildTool == BuildTool.MAVEN
                ? "<project><groupId>acme</groupId><artifactId>app</artifactId><version>1.0.0</version></project>"
                : "version=1.0.0\ngroup=acme\n";
        String external = buildTool == BuildTool.MAVEN
                ? original.replace("</project>", "<description>external B</description></project>")
                : original + "external=B\n";
        var remoteContent = new AtomicReference<>(original);
        var remoteRevision = new AtomicReference<>(ORIGINAL_REVISION);
        var writes = new AtomicInteger();
        String endpoint = "https://gitlab.invalid/api/v4/projects/acme%2Frepo/repository/files/" + path;
        String response = mapper.writeValueAsString(Map.of(
                "content", Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8)),
                "encoding", "base64", "last_commit_id", ORIGINAL_REVISION));
        server.expect(requestTo(endpoint + "?ref=main")).andExpect(method(HttpMethod.HEAD))
                .andRespond(withSuccess());
        server.expect(requestTo(endpoint + "?ref=main")).andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    if (concurrentChange) {
                        remoteContent.set(external);
                        remoteRevision.set("b".repeat(40));
                    }
                    return withSuccess(response, MediaType.APPLICATION_JSON).createResponse(request);
                });
        server.expect(requestTo(endpoint)).andExpect(method(HttpMethod.PUT))
                .andRespond(request -> {
                    writes.incrementAndGet();
                    var payload = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals(ORIGINAL_REVISION, payload.path("last_commit_id").asText());
                    if (!remoteRevision.get().equals(payload.path("last_commit_id").asText())) {
                        return withStatus(HttpStatus.BAD_REQUEST).body("{\"message\":\"file changed\"}")
                                .contentType(MediaType.APPLICATION_JSON).createResponse(request);
                    }
                    remoteContent.set(payload.path("content").asText());
                    return withSuccess("{\"file_path\":\"" + path + "\"}", MediaType.APPLICATION_JSON).createResponse(request);
                });

        RepoId repoId = RepoId.newId();
        var repo = mock(CodeRepository.class);
        when(repo.getGitProvider()).thenReturn(GitProvider.GITLAB);
        when(repo.getCloneUrl()).thenReturn(REPO);
        var repos = mock(CodeRepositoryPort.class);
        when(repos.findById(repoId)).thenReturn(Optional.of(repo));
        VersionUpdaterPort updater = buildTool == BuildTool.MAVEN
                ? new MavenVersionUpdaterAdapter(files, repos) : new GradleVersionUpdaterAdapter(files, repos);
        var request = buildTool == BuildTool.MAVEN
                ? VersionUpdateRequest.forMaven(repoId, "main", ".", "2.0.0", path)
                : VersionUpdateRequest.forGradle(repoId, "main", ".", "2.0.0", path);

        var result = updater.update(request);
        assertEquals(!concurrentChange, result.success());
        assertEquals(1, writes.get());
        if (concurrentChange) assertEquals(external, remoteContent.get());
        else assertTrue(remoteContent.get().contains("2.0.0"));
        server.verify();
    }

    @Test
    void shouldKeepInMemoryWritesConditionalOnOriginalRevision() {
        var files = new InMemoryGitLabFileAdapter();
        files.setFile(REPO, "main", "gradle.properties", "version=1.0.0");
        var original = files.readFile(REPO, "main", "gradle.properties").orElseThrow();
        files.setFile(REPO, "main", "gradle.properties", "version=1.0.0\nexternal=B");
        assertFalse(files.updateFile(REPO, "main", "gradle.properties", original, "version=2.0.0", "update"));
        assertEquals("version=1.0.0\nexternal=B", files.readFile(REPO, "main", "gradle.properties").orElseThrow().content());

        var latest = files.readFile(REPO, "main", "gradle.properties").orElseThrow();
        assertTrue(files.updateFile(REPO, "main", "gradle.properties", latest, "version=2.0.0", "update"));
        assertFalse(files.updateFile(REPO, "main", "gradle.properties", latest, "version=3.0.0", "repeat stale"));
    }

    @Test
    void shouldRejectSnapshotWithoutOriginalRevision() {
        assertThrows(IllegalArgumentException.class, () -> new GitLabFilePort.FileSnapshot("content", null));
        assertThrows(IllegalArgumentException.class, () -> new GitLabFilePort.FileSnapshot("content", ""));
    }
}
