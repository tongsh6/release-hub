package io.releasehub.infrastructure.git;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubGitBranchAdapterTest {

    private static WireMockServer wireMockServer;
    private final GitHubGitBranchAdapter adapter = new GitHubGitBranchAdapter(new org.springframework.boot.web.client.RestTemplateBuilder());

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void injectRestTemplate() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1).build();
        adapter.setRestTemplate(new RestTemplate(
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient)));
    }

    private String baseUrl() {
        return "http://localhost:" + wireMockServer.port();
    }

    @Test
    void shouldSupportGitHubProvider() {
        assertTrue(adapter.supports(GitProvider.GITHUB));
        assertFalse(adapter.supports(GitProvider.GITLAB));
    }

    @Test
    void shouldCreateBranchSuccessfully() {
        wireMockServer.stubFor(get(urlPathEqualTo("/repos/acme/releasehub/branches/main"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{\"commit\":{\"sha\":\"abc123\"}}")));
        wireMockServer.stubFor(post(urlPathEqualTo("/repos/acme/releasehub/git/refs"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{}")));

        assertTrue(adapter.createBranch(baseUrl() + "/acme/releasehub.git", "token", "release/RW-1", "main"));
    }

    @Test
    void shouldReturnConflictWhenMergeHasConflict() {
        wireMockServer.stubFor(post(urlPathEqualTo("/repos/acme/releasehub/merges"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(409)
                        .withBody("{\"message\":\"merge conflict\"}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl() + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.CONFLICT, result.status());
        assertTrue(result.detail().contains("merge conflict"));
    }

    @Test
    void shouldReturnMissingWhenBranchNotFound() {
        wireMockServer.stubFor(get(urlPathEqualTo("/repos/acme/releasehub/branches/release/RW-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(404)));

        GitBranchPort.BranchStatus status = adapter.getBranchStatus(
                baseUrl() + "/acme/releasehub.git", "token", "release/RW-1");

        assertFalse(status.exists());
        assertNull(status.latestCommit());
    }

    @Test
    void shouldCheckMergeabilityAsMergeable() {
        wireMockServer.stubFor(post(urlPathEqualTo("/repos/acme/releasehub/pulls"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"number\":301,\"mergeable\":true}")));

        // PATCH to close the PR — any method works for the stub
        wireMockServer.stubFor(any(urlPathEqualTo("/repos/acme/releasehub/pulls/301"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                baseUrl() + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1");

        assertEquals(true, result.canMerge(), "DEBUG detail=" + result.detail());
    }

    @Test
    void shouldCheckMergeabilityAsConflict() {
        wireMockServer.stubFor(post(urlPathEqualTo("/repos/acme/releasehub/pulls"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"number\":302,\"mergeable\":false}")));

        wireMockServer.stubFor(any(urlPathEqualTo("/repos/acme/releasehub/pulls/302"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                baseUrl() + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1");

        assertFalse(result.canMerge());
        assertEquals(true, result.detail().contains("conflict"), "DEBUG detail=" + result.detail());
    }
}
