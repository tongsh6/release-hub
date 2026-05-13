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
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest(httpPort = 0)
class GitLabGitBranchAdapterTest {

    private final GitLabGitBranchAdapter adapter = new GitLabGitBranchAdapter(
            new org.springframework.boot.web.client.RestTemplateBuilder());

    /**
     * Adapter 通过 {@code uri(...)} 把 endpoint 包装为 URI，避免 RestTemplate 二次编码。
     * 单次 encode：{@code /} → {@code %2F}。WireMock 收到正确的单编码路径。
     */
    private static final String ENC = "/api/v4/projects/acme%2Freleasehub";

    @BeforeEach
    void injectRestTemplate() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1).build();
        adapter.setRestTemplate(new RestTemplate(
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient)));
    }

    private static String baseUrl(WireMockRuntimeInfo wm) {
        return "http://localhost:" + wm.getHttpPort();
    }

    @Test
    void shouldSupportGitLabProvider() {
        assertTrue(adapter.supports(GitProvider.GITLAB));
        assertFalse(adapter.supports(GitProvider.GITHUB));
    }

    @Test
    void shouldCreateBranchSuccessfully(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/repository/branches"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{}")));

        assertTrue(adapter.createBranch(baseUrl(wm) + "/acme/releasehub.git", "token", "release/RW-1", "main"));
    }

    @Test
    void shouldMergeSuccessfully(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":101}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/101/merge"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.SUCCESS, result.status());
    }

    @Test
    void shouldReturnConflictWhenMergeFailed(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(400)
                        .withBody("{\"message\":\"cannot be merged due to conflict\"}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.CONFLICT, result.status());
    }

    @Test
    void shouldTreatNoCommitsBetweenBranchesAsSuccessfulMerge(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(400)
                        .withBody("{\"message\":[\"No commits between feature/ITER-1 and release/RW-1\"]}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.SUCCESS, result.status());
    }

    @Test
    void shouldWaitForGitLabMergeRequestReadinessBeforeMerging(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":102,\"merge_status\":\"unchecked\"}")));
        stubFor(get(urlPathEqualTo(ENC + "/merge_requests/102"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{\"iid\":102,\"merge_status\":\"can_be_merged\"}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/102/merge"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.SUCCESS, result.status());
    }

    @Test
    void shouldTreatGitLabCommitsStatusAsNoOpMerge(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":104,\"merge_status\":\"unchecked\"}")));
        stubFor(get(urlPathEqualTo(ENC + "/merge_requests/104"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{\"iid\":104,\"detailed_merge_status\":\"commits_status\"}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/104"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.SUCCESS, result.status());
        verify(putRequestedFor(urlPathEqualTo(ENC + "/merge_requests/104")));
    }

    @Test
    void shouldCloseMergeRequestWhenMergeEndpointFails(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":103,\"merge_status\":\"can_be_merged\"}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/103/merge"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(405)
                        .withBody("{\"message\":\"405 Method Not Allowed\"}")));
        stubFor(get(urlPathEqualTo(ENC + "/merge_requests/103"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{\"iid\":103,\"merge_status\":\"can_be_merged\"}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/103"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.FAILED, result.status());
        verify(putRequestedFor(urlPathEqualTo(ENC + "/merge_requests/103")));
    }

    @Test
    void shouldTreatCommitsStatusAfterMergeEndpoint405AsNoOpMerge(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":105,\"merge_status\":\"can_be_merged\"}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/105/merge"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(405)
                        .withBody("{\"message\":\"405 Method Not Allowed\"}")));
        stubFor(get(urlPathEqualTo(ENC + "/merge_requests/105"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{\"iid\":105,\"detailed_merge_status\":\"commits_status\"}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/105"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeResult result = adapter.mergeBranch(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1", "merge");

        assertEquals(MergeStatus.SUCCESS, result.status());
        verify(putRequestedFor(urlPathEqualTo(ENC + "/merge_requests/105")));
    }

    @Test
    void shouldGetBranchStatusSuccessfully(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(ENC + "/repository/branches/release%2FRW-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{\"commit\":{\"id\":\"abc123\"}}")));

        GitBranchPort.BranchStatus status = adapter.getBranchStatus(
                baseUrl(wm) + "/acme/releasehub.git", "token", "release/RW-1");

        assertTrue(status.exists());
        assertEquals("abc123", status.latestCommit());
    }

    @Test
    void shouldCheckMergeabilityAsMergeable(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":201,\"merge_status\":\"can_be_merged\"}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/201"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        assertTrue(adapter.checkMergeability(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1").canMerge());
    }

    @Test
    void shouldCheckMergeabilityAsConflict(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":202,\"merge_status\":\"cannot_be_merged\"}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/202"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1");

        assertFalse(result.canMerge());
    }

    @Test
    void shouldTreatNoCommitsBetweenBranchesAsMergeable(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(400)
                        .withBody("{\"message\":[\"No commits between feature/ITER-1 and release/RW-1\"]}")));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1");

        assertTrue(result.canMerge());
    }

    @Test
    void shouldTreatGitLabCommitsStatusAsMergeable(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(201)
                        .withBody("{\"iid\":203,\"merge_status\":\"unchecked\"}")));
        stubFor(get(urlPathEqualTo(ENC + "/merge_requests/203"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{\"iid\":203,\"detailed_merge_status\":\"commits_status\"}")));
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/203"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withStatus(200)
                        .withBody("{}")));

        GitBranchPort.MergeabilityResult result = adapter.checkMergeability(
                baseUrl(wm) + "/acme/releasehub.git", "token", "feature/ITER-1", "release/RW-1");

        assertTrue(result.canMerge());
        verify(putRequestedFor(urlPathEqualTo(ENC + "/merge_requests/203")));
    }
}
