package io.releasehub.infrastructure.git;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest(httpPort = 0)
class GitHubGitBranchAdapterTest {

    private final GitHubGitBranchAdapter adapter = new GitHubGitBranchAdapter(
            new org.springframework.boot.web.client.RestTemplateBuilder());

    @BeforeEach
    void injectRestTemplate(WireMockRuntimeInfo wm) {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1).build();
        adapter.setRestTemplate(new RestTemplate(
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient)));
    }

    private static String baseUrl(WireMockRuntimeInfo wm) {
        return "http://localhost:" + wm.getHttpPort();
    }

    @Test
    void shouldSupportGitHubProvider() {
        assertTrue(adapter.supports(GitProvider.GITHUB));
        assertFalse(adapter.supports(GitProvider.GITLAB));
    }

    @Test
    void shouldCreateBranchSuccessfully(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/repos/acme/releasehub/branches/main"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{\"commit\":{\"sha\":\"abc123\"}}")));
        stubFor(post(urlPathEqualTo("/repos/acme/releasehub/git/refs"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{}")));

        assertTrue(adapter.createBranch(baseUrl(wm) + "/acme/releasehub.git", "token", "release/RW-1", "main"));
    }

    @Test
    void shouldReturnConflictWhenMergeHasConflict(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/repos/acme/releasehub/merges"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(409)
                        .withBody("{\"message\":\"merge conflict\"}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.CONFLICT, result.status());
        assertTrue(result.detail().contains("merge conflict"));
    }

    @Test
    void shouldReturnMissingWhenBranchNotFound(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/repos/acme/releasehub/branches/release/RW-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(404)));

        GitBranchPort.BranchStatus status = adapter.getBranchStatus(
                baseUrl(wm) + "/acme/releasehub.git", "token", "release/RW-1");

        assertFalse(status.exists());
        assertNull(status.latestCommit());
    }

    @Test
    void shouldCheckMergeabilityAsMergeable(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/repos/acme/releasehub/pulls"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"number\":301,\"mergeable\":true}")));
        stubFor(request("PATCH", urlPathEqualTo("/repos/acme/releasehub/pulls/301"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1");

        assertTrue(result.canMerge());
    }

    @Test
    void shouldCheckMergeabilityAsConflict(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo("/repos/acme/releasehub/pulls"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"number\":302,\"mergeable\":false}")));
        stubFor(request("PATCH", urlPathEqualTo("/repos/acme/releasehub/pulls/302"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1");

        assertFalse(result.canMerge());
        assertTrue(result.detail().contains("conflict"));
    }
}
