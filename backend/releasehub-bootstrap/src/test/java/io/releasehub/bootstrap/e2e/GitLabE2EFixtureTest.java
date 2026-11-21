package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.gitlab.GitLabPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunExecutionStatus;
import io.releasehub.domain.run.RunId;
import io.releasehub.domain.run.RunType;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.interfaces.api.settings.SettingsController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 仅验证外部夹具的配置/HTTP 契约，真实 Slice 测试仍连接隔离 GitLab。 */
class GitLabE2EFixtureTest {
    private static final String BASE_URL = "http://isolated-gitlab.test:9080";
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate client = new RestTemplate();
    private final List<String> projectPaths = new ArrayList<>();
    private MockRestServiceServer server;

    @BeforeEach
    void bindFixtureClient() {
        server = MockRestServiceServer.bindTo(client).build();
    }

    @Test
    void createsDistinctInitializedProjectsUnderTheExplicitTestAccount() throws IOException {
        expectAccount("e2e-user");
        expectProject(41, null);
        expectMain(41, "a".repeat(40));
        expectAccount("e2e-user");
        expectProject(42, null);
        expectMain(42, "b".repeat(40));

        var first = E2ETestFixtures.createIsolatedGitLabProject(client, environment());
        var second = E2ETestFixtures.createIsolatedGitLabProject(client, environment());

        assertThat(first.projectId()).isEqualTo("41");
        assertThat(second.projectId()).isEqualTo("42");
        assertThat(first.cloneUrl()).startsWith(BASE_URL + "/e2e-user/releasehub-e2e-").endsWith(".git");
        assertThat(second.cloneUrl()).isNotEqualTo(first.cloneUrl());
        assertThat(projectPaths).hasSize(2).doesNotHaveDuplicates();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"E2E_GITLAB_URL", "E2E_GITLAB_TOKEN", "E2E_GITLAB_USER"})
    void rejectsMissingOrBlankConfigurationBeforeAnyHttp(String key) {
        Map<String, String> config = new HashMap<>(environment());
        config.remove(key);
        assertThatThrownBy(() -> E2ETestFixtures.createIsolatedGitLabProject(client, config))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(key);
        config.put(key, " ");
        assertThatThrownBy(() -> E2ETestFixtures.createIsolatedGitLabProject(client, config))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(key);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://secret@gitlab.example", "http://gitlab.example?token=secret",
            "http://gitlab.example#secret", "file:///gitlab"})
    void rejectsAmbiguousEndpointWithoutEchoingCredentials(String endpoint) {
        Map<String, String> config = new HashMap<>(environment());
        config.put("E2E_GITLAB_URL", endpoint);
        assertThatThrownBy(() -> E2ETestFixtures.createIsolatedGitLabProject(client, config))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("E2E_GITLAB_URL")
                .hasMessageNotContaining("secret");
        server.verify();
    }

    @Test
    void refusesToCreateProjectsForAnotherAuthenticatedAccount() throws IOException {
        expectAccount("another-user");
        assertThatThrownBy(() -> E2ETestFixtures.createIsolatedGitLabProject(client, environment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("E2E_GITLAB_USER");
        server.verify();
        assertThat(projectPaths).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "path_with_namespace", "default_branch"})
    void refusesUnverifiedProjectIdentityBeforeBranchReadsOrRegistration(String field) throws IOException {
        expectAccount("e2e-user");
        expectProject(41, field);
        assertThatThrownBy(() -> E2ETestFixtures.createIsolatedGitLabProject(client, environment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("GitLab");
        server.verify();
    }

    @Test
    void doesNotReturnARepositoryFixtureWithoutAnInitializedMainCommit() throws IOException {
        expectAccount("e2e-user");
        expectProject(41, null);
        expectMain(41, "");
        assertThatThrownBy(() -> E2ETestFixtures.createIsolatedGitLabProject(client, environment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("main");
        server.verify();
    }

    @Test
    void configuresThePersistedGitLabSettingsAfterAnEarlierSliceUsedAnExampleUrl() throws Exception {
        var stored = new AtomicReference<>(new SettingsPort.SettingsGitLab(
                "https://gitlab.e2e-test.example.com", "old-fixture-token"));
        SettingsPort settings = mock(SettingsPort.class);
        when(settings.getGitLab()).thenAnswer(invocation -> Optional.of(stored.get()));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(settings).saveGitLab(any());
        var api = MockMvcBuilders.standaloneSetup(new SettingsController(settings, mock(GitLabPort.class))).build();

        E2ETestFixtures.configureGitLabSettings(api, mapper, "app-fixture-token", BASE_URL, "fixture-only-token");

        assertThat(stored.get()).isEqualTo(new SettingsPort.SettingsGitLab(BASE_URL, "fixture-only-token"));
        verify(settings).saveGitLab(new SettingsPort.SettingsGitLab(BASE_URL, "fixture-only-token"));
        verify(settings).getGitLab();
        server.verify();
    }

    @Test
    void rejectsASettingsWriteWhoseReadbackStillPointsAtAnotherGitLab() {
        SettingsPort settings = mock(SettingsPort.class);
        when(settings.getGitLab()).thenReturn(Optional.of(new SettingsPort.SettingsGitLab(
                "https://gitlab.e2e-test.example.com", "old-fixture-token")));
        var api = MockMvcBuilders.standaloneSetup(new SettingsController(settings, mock(GitLabPort.class))).build();

        assertThatThrownBy(() -> E2ETestFixtures.configureGitLabSettings(
                api, mapper, "app-fixture-token", BASE_URL, "fixture-only-token"))
                .isInstanceOf(AssertionError.class);

        verify(settings).saveGitLab(new SettingsPort.SettingsGitLab(BASE_URL, "fixture-only-token"));
        verify(settings).getGitLab();
        server.verify();
    }

    @Test
    void completedWindowReceiptUsesThePersistedRunIdValue() throws InterruptedException {
        String runId = "c2a00000-0000-4000-8000-000000000001";
        Run run = mock(Run.class);
        when(run.getId()).thenReturn(RunId.of(runId));
        when(run.getRunType()).thenReturn(RunType.WINDOW_ORCHESTRATION);
        when(run.getExecutionVersion()).thenReturn(Run.ORCHESTRATION_PROTOCOL_VERSION);
        when(run.getExecutionStatus()).thenReturn(RunExecutionStatus.DONE);
        RunPort runs = mock(RunPort.class);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(runs.findTasks(runId)).thenReturn(List.of(new RunPort.TaskView(
                "task", runId, "item", ActionType.TRY_MERGE, 4, "COMPLETED", null, null, null)));

        assertThat(E2ETestFixtures.awaitWindowRun(runs, runId)).isSameAs(run);
        verify(runs).findById(runId);
        verify(runs).findTasks(runId);
        org.mockito.Mockito.verifyNoMoreInteractions(runs);
    }

    private Map<String, String> environment() {
        return Map.of("E2E_GITLAB_URL", BASE_URL, "E2E_GITLAB_TOKEN", "fixture-only-token",
                "E2E_GITLAB_USER", "e2e-user");
    }

    private void expectAccount(String username) throws IOException {
        server.expect(requestTo(BASE_URL + "/api/v4/user")).andExpect(method(GET))
                .andExpect(header("PRIVATE-TOKEN", "fixture-only-token"))
                .andRespond(withSuccess(mapper.writeValueAsString(Map.of("username", username)), MediaType.APPLICATION_JSON));
    }

    private void expectProject(int projectId, String invalidField) {
        server.expect(requestTo(BASE_URL + "/api/v4/projects")).andExpect(method(POST))
                .andExpect(header("PRIVATE-TOKEN", "fixture-only-token"))
                .andRespond(request -> {
                    JsonNode body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    String projectPath = body.path("path").asText();
                    projectPaths.add(projectPath);
                    assertThat(projectPath).startsWith("releasehub-e2e-");
                    assertThat(body.path("name").asText()).isEqualTo(projectPath);
                    assertThat(body.path("visibility").asText()).isEqualTo("private");
                    assertThat(body.path("initialize_with_readme").asBoolean()).isTrue();
                    assertThat(body.path("default_branch").asText()).isEqualTo("main");
                    assertThat(body.path("auto_devops_enabled").asBoolean()).isFalse();
                    var project = mapper.createObjectNode()
                            .put("id", projectId).put("path", projectPath)
                            .put("path_with_namespace", "e2e-user/" + projectPath).put("default_branch", "main")
                            .put("http_url_to_repo", "http://gitlab.local/e2e-user/" + projectPath + ".git");
                    if ("id".equals(invalidField)) project.put("id", 0);
                    if ("path_with_namespace".equals(invalidField)) project.put("path_with_namespace", "another/" + projectPath);
                    if ("default_branch".equals(invalidField)) project.put("default_branch", "other");
                    return withSuccess(mapper.writeValueAsString(project), MediaType.APPLICATION_JSON).createResponse(request);
                });
    }

    private void expectMain(int projectId, String sha) throws IOException {
        server.expect(requestTo(BASE_URL + "/api/v4/projects/" + projectId + "/repository/branches/main"))
                .andExpect(method(GET)).andExpect(header("PRIVATE-TOKEN", "fixture-only-token"))
                .andRespond(withSuccess(mapper.writeValueAsString(Map.of("name", "main",
                        "commit", Map.of("id", sha))), MediaType.APPLICATION_JSON));
    }
}
