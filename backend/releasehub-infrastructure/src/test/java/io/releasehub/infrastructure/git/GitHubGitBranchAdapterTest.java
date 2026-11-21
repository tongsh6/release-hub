package io.releasehub.infrastructure.git;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest(httpPort = 0)
class GitHubGitBranchAdapterTest {
    private static final String API = "/repos/acme/releasehub";
    private static final String SHA = "a".repeat(40);
    private final GitHubGitBranchAdapter adapter = new GitHubGitBranchAdapter(
            new org.springframework.boot.web.client.RestTemplateBuilder());

    @BeforeEach void injectRestTemplate() {
        var client = java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build();
        adapter.setRestTemplate(new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory(client)));
        stubFor(get(urlPathEqualTo(API)).willReturn(okJson("{\"id\":7}")));
        stubFor(get(urlPathEqualTo(API + "/branches/feature%2FITER-1")).willReturn(okJson(
                "{\"name\":\"feature/ITER-1\",\"commit\":{\"sha\":\"" + SHA + "\"}}")));
        stubFor(get(urlPathEqualTo(API + "/pulls")).willReturn(okJson("[]")));
    }

    @Test void shouldSupportGitHubProvider() {
        assertTrue(adapter.supports(GitProvider.GITHUB));
        assertFalse(adapter.supports(GitProvider.GITLAB));
    }

    @Test void shouldCreateBranchSuccessfully(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(API + "/branches/main")).willReturn(okJson("{\"commit\":{\"sha\":\"abc123\"}}")));
        stubFor(post(urlPathEqualTo(API + "/git/refs")).willReturn(created().withHeader("Content-Type", "application/json").withBody("{}")));
        assertTrue(adapter.createBranch(repo(wm), "token", "release/RW-1", "main"));
    }

    @Test void shouldReturnConflictWhenMergeHasConflict(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(API + "/merges")).willReturn(aResponse().withStatus(409).withBody("{\"message\":\"merge conflict\"}")));
        var result = adapter.mergeBranch(repo(wm), "token", "feature/ITER-1", "release/RW-1", "merge");
        assertEquals(MergeStatus.CONFLICT, result.status());
        assertTrue(result.detail().contains("merge conflict"));
    }

    @Test void shouldReturnMissingWhenBranchNotFound(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(API + "/branches/release/RW-1")).willReturn(notFound()));
        var status = adapter.getBranchStatus(repo(wm), "token", "release/RW-1");
        assertFalse(status.exists());
        assertNull(status.latestCommit());
    }

    @Test void shouldCheckExistingPullRequestWithoutWrites(WireMockRuntimeInfo wm) {
        existingPr("true", "clean");
        assertTrue(probe(wm).canMerge());
        verifyNoWrites();
    }

    @Test void shouldNotTreatUnknownPullRequestMergeabilityAsSafe(WireMockRuntimeInfo wm) {
        existingPr("null", "unknown");
        assertFalse(probe(wm).canMerge());
        verifyNoWrites();
    }

    @Test void shouldCheckConflictWithoutClosingExistingPullRequest(WireMockRuntimeInfo wm) {
        existingPr("false", "dirty");
        var result = probe(wm);
        assertFalse(result.canMerge());
        assertEquals(GitBranchPort.MergeabilityFailure.CONFLICT, result.failure());
        verifyNoWrites();
    }

    @Test void shouldNotCreateTemporaryPullRequestWhenNoneExists(WireMockRuntimeInfo wm) {
        var result = probe(wm);
        assertFalse(result.canMerge());
        assertEquals(GitBranchPort.MergeabilityFailure.UNKNOWN, result.failure());
        verifyNoWrites();
    }

    private static String repo(WireMockRuntimeInfo wm) { return "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git"; }
    private GitBranchPort.MergeabilityResult probe(WireMockRuntimeInfo wm) {
        return adapter.checkMergeability(repo(wm), "token", "feature/ITER-1", "release/RW-1");
    }
    private static void existingPr(String mergeable, String status) {
        String body = """
                {"number":301,"state":"open","html_url":"https://github.test/acme/releasehub/pull/301",
                 "head":{"ref":"feature/ITER-1","sha":"%s","repo":{"id":7}},
                 "base":{"ref":"release/RW-1","repo":{"id":7}},"mergeable":%s,"mergeable_state":"%s"}
                """.formatted(SHA, mergeable, status);
        stubFor(get(urlPathEqualTo(API + "/pulls")).willReturn(okJson("[" + body + "]")));
        stubFor(get(urlPathEqualTo(API + "/pulls/301")).willReturn(okJson(body)));
    }
    private static void verifyNoWrites() {
        verify(0, postRequestedFor(anyUrl()));
        verify(0, putRequestedFor(anyUrl()));
        verify(0, patchRequestedFor(anyUrl()));
    }
}