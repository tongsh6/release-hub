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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabGitBranchAdapterTest {

    private static WireMockServer wireMockServer;
    private final GitLabGitBranchAdapter adapter = new GitLabGitBranchAdapter(
            new org.springframework.boot.web.client.RestTemplateBuilder());

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

    private static String baseUrl() {
        return "http://localhost:" + wireMockServer.port();
    }

    /**
     * GitLab adapter URL-encodes the project path ({@code acme/releasehub})
     * to {@code acme%2Freleasehub}. WireMock/Jetty may or may not decode
     * {@code %2F} before path matching, so use regex wildcards to handle both.
     */
    private static String path(String suffix) {
        return ".*/api/v4/projects/acme.*releasehub" + suffix;
    }

    @Test
    void shouldSupportGitLabProvider() {
        assertTrue(adapter.supports(GitProvider.GITLAB));
        assertFalse(adapter.supports(GitProvider.GITHUB));
    }

    @Test
    void shouldCreateBranchSuccessfully() {
        wireMockServer.stubFor(post(urlPathMatching(path("/repository/branches")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{}")));

        assertTrue(adapter.createBranch(baseUrl() + "/acme/releasehub.git", "token", "release/RW-1", "main"));
    }

    @Test
    void shouldMergeSuccessfully() {
        wireMockServer.stubFor(post(urlPathMatching(path("/merge_requests")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":101}")));
        wireMockServer.stubFor(put(urlPathMatching(path("/merge_requests/101/merge")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl() + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.SUCCESS, result.status());
    }

    @Test
    void shouldReturnConflictWhenMergeFailed() {
        wireMockServer.stubFor(post(urlPathMatching(path("/merge_requests")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(400)
                        .withBody("{\"message\":\"cannot be merged due to conflict\"}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl() + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.CONFLICT, result.status());
    }

    @Test
    void shouldGetBranchStatusSuccessfully() {
        wireMockServer.stubFor(get(urlPathMatching(path("/repository/branches/release.*RW-1")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{\"commit\":{\"id\":\"abc123\"}}")));

        GitBranchPort.BranchStatus status = adapter.getBranchStatus(
                baseUrl() + "/acme/releasehub.git", "token", "release/RW-1");

        assertTrue(status.exists());
        assertEquals("abc123", status.latestCommit());
    }

    @Test
    void shouldCheckMergeabilityAsMergeable() {
        wireMockServer.stubFor(post(urlPathMatching(path("/merge_requests")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":201,\"merge_status\":\"can_be_merged\"}")));
        wireMockServer.stubFor(put(urlPathMatching(path("/merge_requests/201")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        assertTrue(adapter.checkMergeability(
                baseUrl() + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1").canMerge());
    }

    @Test
    void shouldCheckMergeabilityAsConflict() {
        wireMockServer.stubFor(post(urlPathMatching(path("/merge_requests")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":202,\"merge_status\":\"cannot_be_merged\"}")));
        wireMockServer.stubFor(put(urlPathMatching(path("/merge_requests/202")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                baseUrl() + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1");

        assertFalse(result.canMerge());
    }
}
