package io.releasehub.infrastructure.git;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest(httpPort = 0)
class GitLabGitBranchAdapterTest {
    private static final String ENC = "/api/v4/projects/acme%2Freleasehub";
    private static final String NUMERIC = "/api/v4/projects/7";
    private static final String SOURCE = "feature/ITER-1";
    private static final String TARGET = "release/RW-1";
    private static final String SHA = "a".repeat(40);
    private static final String MERGE_SHA = "b".repeat(40);
    private final GitRemoteIdentityResolver identities = org.mockito.Mockito.mock(GitRemoteIdentityResolver.class);
    private final GitLabGitBranchAdapter adapter = new GitLabGitBranchAdapter(
            new org.springframework.boot.web.client.RestTemplateBuilder(), identities);

    @BeforeEach void injectRestTemplate() {
        var client = java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build();
        adapter.setRestTemplate(new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory(client)));
        org.mockito.Mockito.when(identities.resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    var endpoint = GitRemoteIdentityResolver.parse(call.getArgument(0, java.net.URI.class));
                    return new GitRemoteIdentityResolver.Identity("repo", "mapping", "remote", "GITLAB",
                            endpoint.baseUrl(), "7", endpoint);
                });
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/branches/feature%2FITER-1"))
                .willReturn(okJson("{\"name\":\"feature/ITER-1\",\"commit\":{\"id\":\"" + SHA + "\"}}")));
        stubFor(get(urlPathEqualTo(NUMERIC + "/merge_requests"))
                .withQueryParam("state", equalTo("opened")).willReturn(okJson("[]")));
        stubFor(get(urlPathEqualTo(NUMERIC + "/merge_requests"))
                .withQueryParam("state", equalTo("merged")).willReturn(okJson("[]")));
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/commits/" + SHA + "/refs")).willReturn(okJson("[]")));
    }

    @Test void shouldSupportGitLabProvider() {
        assertTrue(adapter.supports(GitProvider.GITLAB));
        assertFalse(adapter.supports(GitProvider.GITHUB));
    }

    @Test void shouldNotDeleteSourceWhenArchiveCreationFails(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/repository/branches")).willReturn(aResponse().withStatus(403)));
        assertFalse(adapter.archiveBranch(repo(wm), "token", SOURCE, "released"));
        verify(0, deleteRequestedFor(anyUrl()));
    }

    @Test void shouldCreateBranchOnlyWithConfirmedResponse(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/repository/branches"))
                .willReturn(created().withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"release/RW-1\",\"commit\":{\"id\":\"" + SHA + "\"}}")));
        assertTrue(adapter.createBranch(repo(wm), "token", TARGET, "main"));
    }

    @ParameterizedTest @ValueSource(ints = {408, 500, 503})
    void shouldExposeUncertainBranchWriteAsTypedUnknown(int status, WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/repository/branches")).willReturn(aResponse().withStatus(status)));
        var error = assertThrows(BusinessException.class, () -> adapter.createBranch(repo(wm), "token", SOURCE, "main"));
        assertEquals(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN, error.getErrorCode());
        verify(1, postRequestedFor(urlPathEqualTo(ENC + "/repository/branches")));
    }

    @Test void shouldExposeLostBranchResponseAsTypedUnknown(WireMockRuntimeInfo wm) {
        var http = new RestTemplate();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(http).build();
        adapter.setRestTemplate(http);
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        "http://localhost:" + wm.getHttpPort() + ENC + "/repository/branches"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withException(
                        new java.net.SocketTimeoutException("response lost")));
        var error = assertThrows(BusinessException.class, () -> adapter.createBranch(repo(wm), "token", SOURCE, "main"));
        assertEquals(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN, error.getErrorCode());
        server.verify();
    }

    @Test void shouldExposeUncertainTagWriteAsTypedUnknown(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/repository/tags")).willReturn(aResponse().withStatus(503)));
        var result = adapter.createTag(repo(wm), "token", "7", "v1", SHA, "tag");
        assertEquals(GitWriteResult.Outcome.UNKNOWN, result.outcome());
    }

    @Test void shouldNotAcceptTagResponseAtDifferentCommit(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/repository/tags")).willReturn(created()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"name\":\"v1\",\"commit\":{\"id\":\"" + MERGE_SHA + "\"}}")));
        var result = adapter.createTag(repo(wm), "token", "7", "v1", SHA, "tag");
        assertEquals(GitWriteResult.Outcome.UNKNOWN, result.outcome());
    }

    @Test void shouldNotCollapseUncertainPipelineIntoAbsentConfiguration(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/pipeline")).willReturn(aResponse().withStatus(503)));
        var result = adapter.triggerPipeline(repo(wm), "token", "7", "v1", SHA);
        assertEquals(GitWriteResult.Outcome.UNKNOWN, result.outcome());
    }

    @Test void shouldNotAcceptPipelineIdWithoutProjectRefAndCommitEvidence(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/pipeline")).willReturn(created()
                .withHeader("Content-Type", "application/json").withBody("{\"id\":321}")));
        var result = adapter.triggerPipeline(repo(wm), "token", "7", "v1", SHA);
        assertEquals(GitWriteResult.Outcome.UNKNOWN, result.outcome());
    }

    @Test void shouldExposeUncertainDeleteAsTypedUnknown(WireMockRuntimeInfo wm) {
        stubFor(delete(urlPathEqualTo(ENC + "/repository/branches/feature%2FITER-1")).willReturn(aResponse().withStatus(408)));
        var error = assertThrows(BusinessException.class, () -> adapter.deleteBranch(repo(wm), "token", SOURCE));
        assertEquals(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN, error.getErrorCode());
    }

    @Test void shouldExposeIncompleteBranchResponseAsTypedUnknown(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/repository/branches")).willReturn(created()
                .withHeader("Content-Type", "application/json").withBody("{}")));
        var error = assertThrows(BusinessException.class, () -> adapter.createBranch(repo(wm), "token", SOURCE, "main"));
        assertEquals(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN, error.getErrorCode());
    }

    @Test void shouldPreserveTypedUnknownWhenArchiveCreationCannotBeConfirmed(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/repository/branches")).willReturn(aResponse().withStatus(503)));
        var error = assertThrows(BusinessException.class, () -> adapter.archiveBranch(repo(wm), "token", SOURCE, "released"));
        assertEquals(ErrorCode.GIT_WRITE_OUTCOME_UNKNOWN, error.getErrorCode());
        verify(0, deleteRequestedFor(anyUrl()));
    }

    @Test void shouldMergeSuccessfullyWithExactSourceSha(WireMockRuntimeInfo wm) {
        createMr(101, "mergeable");
        mergeMr(101);
        var result = merge(wm);
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertEquals(GitBranchPort.MrDisposition.CREATED, result.mrEvidence().disposition());
        assertEquals("7", result.mrEvidence().projectId());
        assertEquals(MERGE_SHA, result.mrEvidence().mergeSha());
        verify(putRequestedFor(urlPathEqualTo(ENC + "/merge_requests/101/merge"))
                .withRequestBody(matchingJsonPath("$.sha", equalTo(SHA)))
                .withRequestBody(matchingJsonPath("$.should_remove_source_branch", equalTo("false"))));
    }

    @Test void shouldReuseOpenedMrAndNeverCreateAnother(WireMockRuntimeInfo wm) {
        openedMr(101, "mergeable");
        mergeMr(101);
        var result = merge(wm);
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertEquals(GitBranchPort.MrDisposition.MR_REUSED, result.mrEvidence().disposition());
        assertEquals("101", result.mrEvidence().iid());
        verify(0, postRequestedFor(urlPathEqualTo(ENC + "/merge_requests")));
        verify(1, putRequestedFor(urlPathEqualTo(ENC + "/merge_requests/101/merge")));
    }

    @Test void shouldKeepConflictedMrOpen(WireMockRuntimeInfo wm) {
        createMr(101, "conflict");
        var result = merge(wm);
        assertEquals(MergeStatus.CONFLICT, result.status());
        assertEquals(GitBranchPort.MergeBlockReason.CONFLICT, result.mrEvidence().blockReason());
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test void shouldNotTreatNoCommitsMessageAsSuccessfulMerge(WireMockRuntimeInfo wm) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests")).willReturn(aResponse().withStatus(400)
                .withBody("{\"message\":[\"No commits between feature/ITER-1 and release/RW-1\"]}")));
        assertEquals(MergeStatus.UNKNOWN, merge(wm).status());
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test void shouldSkipEmptyMrOnlyWithConfirmedContainment(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/commits/" + SHA + "/refs"))
                .willReturn(okJson("[{\"type\":\"branch\",\"name\":\"release/RW-1\"}]")));
        var result = merge(wm);
        assertEquals(MergeStatus.SUCCESS, result.status());
        assertEquals(GitBranchPort.MrDisposition.ALREADY_MERGED, result.mrEvidence().disposition());
        verifyNoWrites();
    }

    @Test void shouldWaitForReadinessBeforeMerging(WireMockRuntimeInfo wm) {
        createMr(102, "checking");
        stubFor(get(urlPathEqualTo(NUMERIC + "/merge_requests/102")).willReturn(okJson(mr(102, "opened", "mergeable"))));
        mergeMr(102);
        assertEquals(MergeStatus.SUCCESS, merge(wm).status());
        verify(1, getRequestedFor(urlPathEqualTo(NUMERIC + "/merge_requests/102")));
        verify(1, postRequestedFor(urlPathEqualTo(ENC + "/merge_requests")));
    }

    @Test void shouldKeepCommitsStatusUnknownWithoutClosingMr(WireMockRuntimeInfo wm) {
        createMr(104, "commits_status");
        assertEquals(MergeStatus.UNKNOWN, merge(wm).status());
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test void shouldKeepRejectedMergeBlockedWithoutHiddenRetry(WireMockRuntimeInfo wm) {
        createMr(103, "mergeable");
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/103/merge")).willReturn(aResponse().withStatus(405)));
        var result = merge(wm);
        assertEquals(MergeStatus.BLOCKED, result.status());
        assertEquals("103", result.mrEvidence().iid());
        verify(1, putRequestedFor(urlPathEqualTo(ENC + "/merge_requests/103/merge")));
        verify(0, putRequestedFor(urlPathEqualTo(ENC + "/merge_requests/103")));
    }

    @Test void shouldKeepUnknownMergeUnreplayed(WireMockRuntimeInfo wm) {
        createMr(103, "mergeable");
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/103/merge")).willReturn(aResponse().withStatus(503)));
        var result = merge(wm);
        assertEquals(MergeStatus.UNKNOWN, result.status());
        assertEquals("103", result.mrEvidence().iid());
        verify(1, putRequestedFor(urlPathEqualTo(ENC + "/merge_requests/103/merge")));
        verify(1, postRequestedFor(urlPathEqualTo(ENC + "/merge_requests")));
    }

    @Test void shouldRequireMergedEvidenceFromSuccessfulHttpResponse(WireMockRuntimeInfo wm) {
        createMr(103, "mergeable");
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/103/merge")).willReturn(okJson("{}")));
        assertEquals(MergeStatus.UNKNOWN, merge(wm).status());
    }

    @Test void shouldGetBranchStatusSuccessfully(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/branches/release%2FRW-1"))
                .willReturn(okJson("{\"name\":\"release/RW-1\",\"commit\":{\"id\":\"" + SHA + "\"}}")));
        var status = adapter.getBranchStatus(repo(wm), "token", TARGET);
        assertTrue(status.exists());
        assertEquals(SHA, status.latestCommit());
        verifyNoWrites();
    }

    @Test void shouldReturnMissingBranchOnlyOnNotFound(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/branches/release%2FRW-1"))
                .willReturn(aResponse().withStatus(404)));
        var status = adapter.getBranchStatus(repo(wm), "token", TARGET);
        assertFalse(status.exists());
        assertNull(status.latestCommit());
        verifyNoWrites();
    }

    @Test void shouldRejectEmptyBranchReadEvidence(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/branches/release%2FRW-1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        var error = assertThrows(BusinessException.class,
                () -> adapter.getBranchStatus(repo(wm), "token", TARGET));
        assertEquals(ErrorCode.GIT_IDENTITY_UNAVAILABLE, error.getErrorCode());
        verifyNoWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"release/RW-1\",\"commit\":{}}",
            "{\"name\":\"release/RW-1\",\"commit\":{\"id\":\"abc123\"}}",
            "{\"name\":\"release/RW-1\",\"commit\":{\"id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}}"})
    void shouldRejectIncompleteBranchReadEvidence(String body, WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/branches/release%2FRW-1")).willReturn(okJson(body)));
        var error = assertThrows(BusinessException.class,
                () -> adapter.getBranchStatus(repo(wm), "token", TARGET));
        assertEquals(ErrorCode.GIT_IDENTITY_UNAVAILABLE, error.getErrorCode());
        verifyNoWrites();
    }

    @Test void shouldRejectMismatchedBranchReadIdentity(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/repository/branches/release%2FRW-1"))
                .willReturn(okJson("{\"name\":\"private-unexpected-branch\",\"commit\":{\"id\":\"" + SHA + "\"}}")));
        var error = assertThrows(BusinessException.class,
                () -> adapter.getBranchStatus(repo(wm), "sensitive-token-fixture", TARGET));
        assertEquals(ErrorCode.GIT_IDENTITY_UNAVAILABLE, error.getErrorCode());
        assertFalse(error.getMessage().contains("private-unexpected-branch"));
        assertFalse(error.getMessage().contains("sensitive-token-fixture"));
        assertNull(error.getCause());
        verifyNoWrites();
    }

    @Test void shouldListAllBranchesWhenPrefixIsBlank(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo(ENC + "/repository/branches?per_page=100"))
                .willReturn(okJson("[{\"name\":\"main\"},{\"name\":\"feature/ITER-1\"},{\"name\":\"legacy_branch\"}]")));
        assertEquals(List.of("main", SOURCE, "legacy_branch"), adapter.listBranches(repo(wm), "token", ""));
    }

    @Test void shouldCheckExistingMrWithoutAnyWrites(WireMockRuntimeInfo wm) {
        openedMr(201, "mergeable");
        assertTrue(adapter.checkMergeability(repo(wm), "token", SOURCE, TARGET).canMerge());
        verifyNoWrites();
    }

    @Test void shouldReportConflictFromExistingMrWithoutClosingIt(WireMockRuntimeInfo wm) {
        openedMr(202, "conflict");
        var result = adapter.checkMergeability(repo(wm), "token", SOURCE, TARGET);
        assertFalse(result.canMerge());
        assertEquals(GitBranchPort.MergeabilityFailure.CONFLICT, result.failure());
        verifyNoWrites();
    }

    @Test void shouldLeaveMissingMrUndeterminedWithoutCreatingATemporaryMr(WireMockRuntimeInfo wm) {
        var result = adapter.checkMergeability(repo(wm), "token", SOURCE, TARGET);
        assertFalse(result.canMerge());
        assertEquals(GitBranchPort.MergeabilityFailure.UNKNOWN, result.failure());
        verifyNoWrites();
    }

    @Test void shouldNotTreatCommitsStatusAsMergeable(WireMockRuntimeInfo wm) {
        openedMr(203, "commits_status");
        assertFalse(adapter.checkMergeability(repo(wm), "token", SOURCE, TARGET).canMerge());
        verifyNoWrites();
    }

    private static String repo(WireMockRuntimeInfo wm) { return "http://localhost:" + wm.getHttpPort() + "/acme/releasehub.git"; }
    private GitBranchPort.MergeResult merge(WireMockRuntimeInfo wm) { return adapter.mergeBranch(repo(wm), "token", SOURCE, TARGET, "merge"); }
    private static void createMr(int id, String status) {
        stubFor(post(urlPathEqualTo(ENC + "/merge_requests")).willReturn(created().withHeader("Content-Type", "application/json")
                .withBody(mr(id, "opened", status))));
        stubFor(get(urlPathEqualTo(NUMERIC + "/merge_requests/" + id)).willReturn(okJson(mr(id, "opened", status))));
    }
    private static void mergeMr(int id) {
        stubFor(put(urlPathEqualTo(ENC + "/merge_requests/" + id + "/merge")).willReturn(okJson(mr(id, "merged", "not_open"))));
    }
    private static void openedMr(int id, String status) {
        stubFor(get(urlPathEqualTo(NUMERIC + "/merge_requests")).withQueryParam("state", equalTo("opened"))
                .willReturn(okJson("[" + mr(id, "opened", status) + "]")));
        stubFor(get(urlPathEqualTo(NUMERIC + "/merge_requests/" + id)).willReturn(okJson(mr(id, "opened", status))));
    }
    private static String mr(int id, String state, String status) {
        return """
                {"iid":%d,"project_id":7,"source_project_id":7,"target_project_id":7,
                 "source_branch":"feature/ITER-1","target_branch":"release/RW-1",
                 "web_url":"https://gitlab.test/acme/releasehub/-/merge_requests/%d",
                 "state":"%s","detailed_merge_status":"%s","sha":"%s","merge_commit_sha":"%s"}
                """.formatted(id, id, state, status, SHA, MERGE_SHA);
    }
    private static void verifyNoWrites() {
        verify(0, postRequestedFor(anyUrl()));
        verify(0, putRequestedFor(anyUrl()));
        verify(0, deleteRequestedFor(anyUrl()));
    }
}