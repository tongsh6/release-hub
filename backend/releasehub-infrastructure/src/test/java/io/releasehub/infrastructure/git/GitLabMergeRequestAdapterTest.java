package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitMergeRequestPort;
import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.application.port.out.GitBranchPort.MergeBlockReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.net.SocketTimeoutException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitLabMergeRequestAdapterTest {
    private static final String PROJECT = "https://gitlab.test/api/v4/projects/7";
    private static final String NAMESPACE = "https://gitlab.test/api/v4/projects/acme%2Frepo";
    private static final String WRITE_ENDPOINT = NAMESPACE + "/merge_requests";
    private static final String ENDPOINT = PROJECT + "/merge_requests";
    private static final String SHA = "a".repeat(40);
    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
    private final GitLabMergeRequestAdapter adapter = new GitLabMergeRequestAdapter(http,
            new GitRemoteIdentityResolver.Identity("repo", "mapping", "remote", "GITLAB", "https://gitlab.test", "7",
                    GitRemoteIdentityResolver.parse(java.net.URI.create(WRITE_ENDPOINT))), "token");

    @ParameterizedTest
    @CsvSource({
            "conflict,success,CONFLICT",
            "ci_still_running,running,PIPELINE_PENDING",
            "ci_must_pass,failed,PIPELINE_FAILED",
            "ci_must_pass,canceled,PIPELINE_FAILED",
            "ci_must_pass,created,PIPELINE_PENDING",
            "not_approved,success,APPROVAL_REQUIRED",
            "requested_changes,success,APPROVAL_REQUIRED"
    })
    void shouldExplainGitLabGateFromDetailedEvidence(String status, String pipeline, MergeBlockReason reason) {
        server.expect(requestTo(ENDPOINT + "/17")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mr(17, status, pipeline), MediaType.APPLICATION_JSON));
        var result = adapter.read("17");
        assertEquals(GitReadResult.Status.FOUND, result.status());
        assertEquals(GitMergeRequestPort.Readiness.BLOCKED, result.evidence().readiness());
        assertEquals(reason, result.evidence().blockReason());
        assertEquals(SHA, result.evidence().sourceSha());
        assertEquals("7", result.evidence().projectId());
        assertEquals("https://gitlab.test/acme/repo/-/merge_requests/17", result.evidence().url());
        server.verify();
    }

    @Test void shouldDistinguishMissingPipelineFromFailedPipeline() {
        server.expect(requestTo(ENDPOINT + "/17")).andRespond(withSuccess(
                mr(17, "ci_must_pass", "created").replace("\"head_pipeline\":{\"status\":\"created\"}", "\"head_pipeline\":null"),
                MediaType.APPLICATION_JSON));
        var result = adapter.read("17");
        assertEquals(MergeBlockReason.PIPELINE_PENDING, result.evidence().blockReason());
        server.verify();
    }

    @Test void shouldKeepCommitsStatusUnknownInsteadOfClaimingNoChanges() {
        server.expect(requestTo(ENDPOINT + "/17")).andRespond(withSuccess(mr(17, "commits_status", "success"), MediaType.APPLICATION_JSON));
        var result = adapter.read("17");
        assertEquals(GitMergeRequestPort.Readiness.UNKNOWN, result.evidence().readiness());
        assertEquals(MergeBlockReason.UNKNOWN, result.evidence().blockReason());
        server.verify();
    }

    @Test void shouldClassifyActualMergePermissionSeparately() {
        server.expect(requestTo(ENDPOINT + "/17")).andRespond(withSuccess(
                mr(17, "mergeable", "success").replace("\"can_merge\":true", "\"can_merge\":false"), MediaType.APPLICATION_JSON));
        assertEquals(MergeBlockReason.PERMISSION_DENIED, adapter.read("17").evidence().blockReason());
        server.verify();
    }

    @Test void shouldNotInferConflictFromDeprecatedGenericMergeStatus() {
        server.expect(requestTo(ENDPOINT + "/17")).andRespond(withSuccess(
                mr(17, "not_approved", "success").replace("\"detailed_merge_status\":\"not_approved\"",
                        "\"merge_status\":\"cannot_be_merged\""), MediaType.APPLICATION_JSON));
        assertEquals(GitMergeRequestPort.Readiness.UNKNOWN, adapter.read("17").evidence().readiness());
        server.verify();
    }

    @Test void shouldReadStableProjectIdAndExactSourceRevision() {
        server.expect(requestTo(PROJECT + "/repository/branches/feature%2FIT-1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"feature/IT-1\",\"commit\":{\"id\":\"" + SHA + "\"}}", MediaType.APPLICATION_JSON));
        var result = adapter.readSource("feature/IT-1");
        assertEquals("7", result.evidence().projectId());
        assertEquals(SHA, result.evidence().sha());
        server.verify();
    }

    @Test void shouldReadAllPagesBeforeDeterminingOpenedMrIdentity() {
        server.expect(requestTo(listUri(1))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON).header("X-Next-Page", "2"));
        server.expect(requestTo(listUri(2))).andRespond(withSuccess("[" + mr(17, "mergeable", "success") + "]",
                MediaType.APPLICATION_JSON).header("X-Next-Page", ""));
        var result = adapter.find("feature/IT-1", "release/RW-1", GitMergeRequestPort.State.OPENED);
        assertEquals(GitReadResult.Status.FOUND, result.status());
        assertEquals("17", result.evidence().get(0).id());
        server.verify();
    }

    @Test void shouldDiscardPartialLookupWhenLaterPageFails() {
        server.expect(requestTo(listUri(1))).andRespond(withSuccess("[" + mr(17, "mergeable", "success") + "]",
                MediaType.APPLICATION_JSON).header("X-Next-Page", "2"));
        server.expect(requestTo(listUri(2))).andRespond(withServerError());
        var result = adapter.find("feature/IT-1", "release/RW-1", GitMergeRequestPort.State.OPENED);
        assertEquals(GitReadResult.Status.UNAVAILABLE, result.status());
        assertNull(result.evidence());
        server.verify();
    }

    @Test void shouldStopAtPaginationLimitWithoutClaimingLookupComplete() {
        for (int page = 1; page <= 20; page++) {
            server.expect(requestTo(listUri(page))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
                    .header("X-Next-Page", Integer.toString(page + 1)));
        }
        assertEquals(GitReadResult.Status.INCOMPLETE,
                adapter.find("feature/IT-1", "release/RW-1", GitMergeRequestPort.State.OPENED).status());
        server.verify();
    }

    @Test void shouldNotReuseSameNamedSourceFromAFork() {
        server.expect(requestTo(listUri(1))).andRespond(withSuccess(
                "[" + mr(17, "mergeable", "success").replace("\"source_project_id\":7", "\"source_project_id\":8") + "]",
                MediaType.APPLICATION_JSON));
        var result = adapter.find("feature/IT-1", "release/RW-1", GitMergeRequestPort.State.OPENED);
        assertEquals(GitReadResult.Status.FOUND, result.status());
        assertTrue(result.evidence().isEmpty());
        server.verify();
    }

    @Test void shouldRejectIncompleteMrIdentityInsteadOfTreatingItAsAbsent() {
        server.expect(requestTo(listUri(1))).andRespond(withSuccess("[{\"iid\":17}]", MediaType.APPLICATION_JSON));
        assertEquals(GitReadResult.Status.INCOMPLETE,
                adapter.find("feature/IT-1", "release/RW-1", GitMergeRequestPort.State.OPENED).status());
        server.verify();
    }

    @Test void shouldConfirmContainmentUsingExactBranchFromAllPages() {
        String path = PROJECT + "/repository/commits/" + SHA + "/refs?type=branch&per_page=100&page=";
        server.expect(requestTo(path + "1")).andRespond(withSuccess("[{\"type\":\"branch\",\"name\":\"main\"}]",
                MediaType.APPLICATION_JSON).header("X-Next-Page", "2"));
        server.expect(requestTo(path + "2")).andRespond(withSuccess("[{\"type\":\"branch\",\"name\":\"release/RW-1\"}]",
                MediaType.APPLICATION_JSON).header("X-Next-Page", ""));
        assertEquals(Boolean.TRUE, adapter.contains("release/RW-1", SHA).evidence());
        server.verify();
    }

    @Test void shouldNeverConvertContainmentFailureIntoAbsence() {
        server.expect(requestTo(PROJECT + "/repository/commits/" + SHA + "/refs?type=branch&per_page=100&page=1"))
                .andRespond(withServerError());
        var result = adapter.contains("release/RW-1", SHA);
        assertEquals(GitReadResult.Status.UNAVAILABLE, result.status());
        assertNull(result.evidence());
        server.verify();
    }

    @Test void shouldReturnUnknownAfterLostWriteResponseWithoutResending() {
        server.expect(requestTo(WRITE_ENDPOINT + "/17/merge")).andExpect(method(HttpMethod.PUT))
                .andExpect(request -> assertEquals(SHA, new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString())
                        .path("sha").asText()))
                .andRespond(withException(new SocketTimeoutException("response lost")));
        assertEquals(GitWriteResult.Outcome.UNKNOWN, adapter.merge("17", SHA, "merge").outcome());
        server.verify();
    }

    @Test void shouldKeepPartialRequestIdWhenCreationResponseIsIncomplete() {
        server.expect(requestTo(WRITE_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"iid\":17}", MediaType.APPLICATION_JSON));
        var result = adapter.create("feature/IT-1", "release/RW-1", SHA, "merge");
        assertEquals(GitWriteResult.Outcome.UNKNOWN, result.outcome());
        assertEquals("17", result.evidence().id());
        server.verify();
    }

    @Test void shouldRejectMergeWithoutReadSourceShaBeforeSendingHttp() {
        assertEquals(GitWriteResult.Outcome.REJECTED, adapter.merge("17", null, "merge").outcome());
        server.verify();
    }

    @Test void shouldClassifyProtectedBranchRejection() {
        server.expect(requestTo(WRITE_ENDPOINT + "/17/merge")).andRespond(withForbiddenRequest()
                .body("{\"message\":\"You are not allowed to merge into this protected branch\"}"));
        var result = adapter.merge("17", SHA, "merge");
        assertEquals(GitWriteResult.Outcome.REJECTED, result.outcome());
        assertEquals("PROTECTED_BRANCH", result.reason());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVAL_REQUIRED sensitive-response-fixture", "HTTP_403", "unclassified-private-fixture"})
    void shouldRejectUnallowlistedCanonicalReasonWithoutEchoingIt(String untrustedReason) throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
                "message", "REMOTE_WRITE_REJECTED", "reason", untrustedReason));
        server.expect(requestTo(WRITE_ENDPOINT + "/17/merge")).andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON).body(body));

        var result = adapter.merge("17", SHA, "merge");

        assertEquals(GitWriteResult.Outcome.REJECTED, result.outcome());
        assertEquals("HTTP_422", result.reason());
        server.verify();
    }

    private static String listUri(int page) {
        return ENDPOINT + "?scope=all&state=opened&source_branch=feature%2FIT-1&target_branch=release%2FRW-1&per_page=100&page=" + page;
    }
    private static String mr(int id, String status, String pipeline) {
        return """
                {"iid":%d,"project_id":7,"source_project_id":7,"target_project_id":7,
                 "source_branch":"feature/IT-1","target_branch":"release/RW-1","state":"opened",
                 "web_url":"https://gitlab.test/acme/repo/-/merge_requests/%d","sha":"%s",
                 "detailed_merge_status":"%s","head_pipeline":{"status":"%s"},"user":{"can_merge":true}}
                """.formatted(id, id, SHA, status, pipeline);
    }
}
