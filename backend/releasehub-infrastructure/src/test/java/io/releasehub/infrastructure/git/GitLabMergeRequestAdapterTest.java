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

    @ParameterizedTest @ValueSource(ints = {200, 202})
    void shouldRejectNonCreatedStatusForCompleteMrResponse(int status) throws Exception {
        server.expect(requestTo(WRITE_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON).body(createdMr("mergeable").toString()));

        assertEquals(GitWriteResult.Outcome.UNKNOWN,
                adapter.create("feature/IT-1", "release/RW-1", SHA, "merge").outcome());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"project_id", "source_project_id", "target_project_id", "source_branch",
            "target_branch", "sha", "web_url", "iid"})
    void shouldRequireEveryCanonicalCreatedMrField(String field) throws Exception {
        var body = createdMr("mergeable");
        body.put("id", 17);
        body.remove(field);
        server.expect(requestTo(WRITE_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body(body.toString()));

        assertEquals(GitWriteResult.Outcome.UNKNOWN,
                adapter.create("feature/IT-1", "release/RW-1", SHA, "merge").outcome());
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"project_id", "source_project_id", "target_project_id"})
    void shouldRejectCreatedMrOutsideTheRequestedProject(String field) throws Exception {
        var body = createdMr("mergeable").put(field, 8);
        server.expect(requestTo(WRITE_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body(body.toString()));

        assertEquals(GitWriteResult.Outcome.UNKNOWN,
                adapter.create("feature/IT-1", "release/RW-1", SHA, "merge").outcome());
        server.verify();
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("invalidCreatedMrFieldTypes")
    void shouldRequireCanonicalCreatedMrFieldTypes(String field, String jsonValue,
                                                   String source, String target, String expectedSha) throws Exception {
        var body = createdMr("mergeable").put("source_branch", source).put("target_branch", target).put("sha", expectedSha);
        body.set(field, new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonValue));
        server.expect(requestTo(WRITE_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body(body.toString()));

        assertEquals(GitWriteResult.Outcome.UNKNOWN, adapter.create(source, target, expectedSha, "merge").outcome());
        server.verify();
    }

    private static java.util.List<org.junit.jupiter.params.provider.Arguments> invalidCreatedMrFieldTypes() {
        return java.util.List.of(
                org.junit.jupiter.params.provider.Arguments.of("project_id", "\"7\"", "feature/IT-1", "release/RW-1", SHA),
                org.junit.jupiter.params.provider.Arguments.of("source_project_id", "\"7\"", "feature/IT-1", "release/RW-1", SHA),
                org.junit.jupiter.params.provider.Arguments.of("target_project_id", "\"7\"", "feature/IT-1", "release/RW-1", SHA),
                org.junit.jupiter.params.provider.Arguments.of("iid", "\"17\"", "feature/IT-1", "release/RW-1", SHA),
                org.junit.jupiter.params.provider.Arguments.of("source_branch", "123", "123", "release/RW-1", SHA),
                org.junit.jupiter.params.provider.Arguments.of("target_branch", "true", "feature/IT-1", "true", SHA),
                org.junit.jupiter.params.provider.Arguments.of("sha", "1".repeat(40), "feature/IT-1", "release/RW-1", "1".repeat(40)),
                org.junit.jupiter.params.provider.Arguments.of("web_url", "[]", "feature/IT-1", "release/RW-1", SHA));
    }

    @ParameterizedTest @ValueSource(strings = {"source_branch", "target_branch", "sha", "state", "web_url"})
    void shouldRejectCreatedMrContradictingTheRequestedCheckpoint(String field) throws Exception {
        var body = createdMr("mergeable");
        body.put(field, switch (field) {
            case "source_branch" -> "feature/OTHER";
            case "target_branch" -> "release/OTHER";
            case "sha" -> "b".repeat(40);
            case "state" -> "merged";
            case "web_url" -> "/relative/merge_requests/17";
            default -> throw new AssertionError(field);
        });
        server.expect(requestTo(WRITE_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body(body.toString()));

        assertEquals(GitWriteResult.Outcome.UNKNOWN,
                adapter.create("feature/IT-1", "release/RW-1", SHA, "merge").outcome());
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"checking", "not_approved", "ci_still_running"})
    void shouldConfirmMrCreationBeforeReadinessAllowsMerging(String readiness) throws Exception {
        server.expect(requestTo(WRITE_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body(createdMr(readiness).toString()));

        var result = adapter.create("feature/IT-1", "release/RW-1", SHA, "merge");

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertEquals(GitMergeRequestPort.State.OPENED, result.evidence().state());
        assertEquals("17", result.evidence().id());
        assertEquals(SHA, result.evidence().sourceSha());
        assertNull(result.evidence().mergeSha());
        server.verify();
    }

    @Test void shouldNotTreatCreatedMrMergeCommitFieldAsAMergeResult() throws Exception {
        var body = createdMr("checking").put("merge_commit_sha", "b".repeat(40));
        server.expect(requestTo(WRITE_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body(body.toString()));

        var result = adapter.create("feature/IT-1", "release/RW-1", SHA, "merge");

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertNull(result.evidence().mergeSha(), "an opened creation response does not prove a merge result");
        server.verify();
    }

    @ParameterizedTest @ValueSource(ints = {40, 64})
    void shouldConfirmCreatedMrWithEquivalentHexSourceRevision(int length) throws Exception {
        String frozen = "aBcD".repeat(length / 4);
        String observed = frozen.toLowerCase(java.util.Locale.ROOT);
        var body = createdMr("checking").put("sha", observed);
        server.expect(requestTo(WRITE_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body(body.toString()));

        var result = adapter.create("feature/IT-1", "release/RW-1", frozen, "merge");

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertEquals(observed, result.evidence().sourceSha());
        assertNull(result.evidence().mergeSha());
        server.verify();
    }

    @ParameterizedTest @ValueSource(ints = {201, 202, 204})
    void mrMergeRequiresSuccessfulStatus(int status) throws Exception {
        assertEquals(GitWriteResult.Outcome.UNKNOWN, mergeResponse(status, mergedMr(), SHA).outcome());
    }

    @ParameterizedTest @ValueSource(strings = {"[]", "null", "true", "\"value\""})
    void mrMergeRequiresAnObjectResponse(String response) throws Exception {
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
        assertEquals(GitWriteResult.Outcome.UNKNOWN, mergeResponse(200, body, SHA).outcome());
    }

    @ParameterizedTest
    @ValueSource(strings = {"project_id", "source_project_id", "target_project_id", "iid",
            "source_branch", "target_branch", "sha", "state", "web_url", "merge_commit_sha"})
    void mrMergeRequiresCanonicalResponseFields(String field) throws Exception {
        var body = mergedMr().put("id", 17).put("squash_commit_sha", "c".repeat(40));
        body.remove(field);
        assertEquals(GitWriteResult.Outcome.UNKNOWN, mergeResponse(200, body, SHA).outcome());
    }

    @ParameterizedTest
    @ValueSource(strings = {"project_id", "source_project_id", "target_project_id", "iid",
            "source_branch", "target_branch", "sha", "state", "web_url", "merge_commit_sha"})
    void mrMergeRequiresOriginalJsonFieldTypes(String field) throws Exception {
        var body = mergedMr();
        String frozen = SHA;
        if (java.util.Set.of("project_id", "source_project_id", "target_project_id", "iid").contains(field)) {
            body.put(field, body.path(field).asText());
        } else if (java.util.Set.of("sha", "merge_commit_sha").contains(field)) {
            String numericSha = "1".repeat(40);
            body.put(field, new java.math.BigInteger(numericSha));
            if ("sha".equals(field)) frozen = numericSha;
        } else {
            body.put(field, true);
        }
        assertEquals(GitWriteResult.Outcome.UNKNOWN, mergeResponse(200, body, frozen).outcome());
    }

    @ParameterizedTest
    @ValueSource(strings = {"project_id", "source_project_id", "target_project_id", "iid"})
    void mrMergeRejectsAContradictingProjectOrIid(String field) throws Exception {
        var body = mergedMr().put("id", 17).put(field, "iid".equals(field) ? 18 : 8);
        assertEquals(GitWriteResult.Outcome.UNKNOWN, mergeResponse(200, body, SHA).outcome());
    }

    @ParameterizedTest
    @ValueSource(strings = {"project_id", "source_project_id", "target_project_id", "iid"})
    void mrMergeRequiresIntegralIdentityNumbers(String field) throws Exception {
        var body = mergedMr();
        body.put(field, body.path(field).asDouble());
        assertEquals(GitWriteResult.Outcome.UNKNOWN, mergeResponse(200, body, SHA).outcome());
    }

    @Test void mrMergeRejectsAnotherSourceRevision() throws Exception {
        var body = mergedMr().put("sha", "b".repeat(40));
        assertEquals(GitWriteResult.Outcome.UNKNOWN, mergeResponse(200, body, SHA).outcome());
    }

    @ParameterizedTest @ValueSource(strings = {"", "null", "not-a-sha"})
    void mrMergeRejectsInvalidMergeCommitSha(String mergeSha) throws Exception {
        var body = mergedMr().put("merge_commit_sha", mergeSha);
        assertEquals(GitWriteResult.Outcome.UNKNOWN, mergeResponse(200, body, SHA).outcome());
    }

    @ParameterizedTest @ValueSource(strings = {"opened", "closed"})
    void mrMergeRejectsAnUnmergedRemoteState(String state) throws Exception {
        var body = mergedMr().put("state", state).put("merge_when_pipeline_succeeds", true);
        assertEquals(GitWriteResult.Outcome.UNKNOWN, mergeResponse(200, body, SHA).outcome());
    }

    @ParameterizedTest @CsvSource({"40,false", "64,false", "40,true", "64,true"})
    void mrMergeAcceptsTheCanonicalResultIncludingExplicitNull(int length, boolean noMergeCommit) throws Exception {
        String frozen = "aBcD".repeat(length / 4);
        String observed = frozen.toUpperCase(java.util.Locale.ROOT);
        String mergeSha = "bCdE".repeat(length / 4);
        var body = mergedMr().put("sha", observed).put("detailed_merge_status", "not_approved")
                .put("squash_commit_sha", "c".repeat(length));
        body.putObject("head_pipeline").put("status", "failed");
        body.putObject("user").put("can_merge", false);
        body.putObject("commit").put("id", "d".repeat(length));
        body.putObject("object").put("sha", "e".repeat(length));
        if (noMergeCommit) body.putNull("merge_commit_sha");
        else body.put("merge_commit_sha", mergeSha);

        var result = mergeResponse(200, body, frozen);

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertEquals(GitMergeRequestPort.State.MERGED, result.evidence().state());
        assertEquals(observed, result.evidence().sourceSha());
        assertEquals(noMergeCommit ? null : mergeSha, result.evidence().mergeSha());
    }

    @Test void mrMergeKeepsLargeIntegerProjectAndIidIdentities() throws Exception {
        long largeProject = 4_294_967_297L;
        long largeIid = 4_294_967_299L;
        var largeAdapter = new GitLabMergeRequestAdapter(http,
                new GitRemoteIdentityResolver.Identity("repo", "mapping", "remote", "GITLAB", "https://gitlab.test",
                        Long.toString(largeProject), GitRemoteIdentityResolver.parse(java.net.URI.create(WRITE_ENDPOINT))), "token");
        var body = mergedMr().put("project_id", largeProject).put("source_project_id", largeProject)
                .put("target_project_id", largeProject).put("iid", largeIid)
                .put("web_url", "https://gitlab.test/acme/repo/-/merge_requests/" + largeIid);
        server.expect(requestTo(WRITE_ENDPOINT + "/" + largeIid + "/merge")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));

        var result = largeAdapter.merge(Long.toString(largeIid), SHA, "merge");

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertEquals(Long.toString(largeProject), result.evidence().projectId());
        assertEquals(Long.toString(largeIid), result.evidence().id());
        server.verify();
    }

    @Test void mrMergeLeavesExactBranchBindingToTheCheckpointConsumer() throws Exception {
        var body = mergedMr().put("source_branch", "feature/OTHER").put("target_branch", "release/OTHER");

        var result = mergeResponse(200, body, SHA);

        assertEquals(GitWriteResult.Outcome.APPLIED, result.outcome());
        assertEquals("feature/OTHER", result.evidence().sourceBranch());
        assertEquals("release/OTHER", result.evidence().targetBranch());
    }


    @ParameterizedTest
    @ValueSource(strings = {"", "Custom merge \"message\" | keep\nthis exact text"})
    void mergeIntentProducerKeepsTheOriginalTypedRequest(String message) throws Exception {
        server.expect(requestTo(WRITE_ENDPOINT + "/17/merge")).andExpect(method(HttpMethod.PUT))
                .andExpect(request -> {
                    assertNull(request.getURI().getRawQuery());
                    assertEquals("7", request.getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID));
                    assertEquals(SHA, request.getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_SHA));
                    var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString());
                    assertEquals(3, body.size());
                    assertAll(
                            () -> assertTrue(body.path("sha").isTextual()),
                            () -> assertEquals(SHA, body.path("sha").textValue()),
                            () -> assertTrue(body.path("merge_commit_message").isTextual()),
                            () -> assertEquals(message, body.path("merge_commit_message").textValue()),
                            () -> assertTrue(body.path("should_remove_source_branch").isBoolean()),
                            () -> assertFalse(body.path("should_remove_source_branch").booleanValue()));
                })
                .andRespond(withSuccess(mergedMr().toString(), MediaType.APPLICATION_JSON));

        assertEquals(GitWriteResult.Outcome.APPLIED, adapter.merge("17", SHA, message).outcome());
        server.verify();
    }

    @Test
    void mergeIntentProducerRejectsMissingMessageBeforeHttp() {
        var result = adapter.merge("17", SHA, null);
        assertEquals(GitWriteResult.Outcome.REJECTED, result.outcome());
        assertEquals("MERGE_REQUEST_INVALID", result.reason());
        server.verify();
    }

    // B9 independent contract: detail identity must survive the raw response boundary.
    @Test
    void createMrIntentDetailAcceptsAllThreeNumericProjectIdentities() throws Exception {
        var body = createdMr("mergeable");
        server.expect(requestTo(ENDPOINT + "/17")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));

        var result = adapter.read("17");

        assertEquals(GitReadResult.Status.FOUND, result.status());
        assertNotNull(result.evidence());
        assertEquals("7", result.evidence().projectId());
        assertEquals("17", result.evidence().id());
        assertEquals("feature/IT-1", result.evidence().sourceBranch());
        assertEquals("release/RW-1", result.evidence().targetBranch());
        assertEquals(SHA, result.evidence().sourceSha());
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"project_id", "source_project_id", "target_project_id"})
    void createMrIntentDetailRequiresEveryProjectIdentity(String field) throws Exception {
        var body = createdMr("mergeable");
        body.remove(field);
        server.expect(requestTo(ENDPOINT + "/17")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));

        var result = adapter.read("17");

        assertEquals(GitReadResult.Status.INCOMPLETE, result.status());
        assertNull(result.evidence());
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"project_id", "source_project_id", "target_project_id"})
    void createMrIntentDetailRejectsAnotherProjectEvenWhenNamesAndShaMatch(String field) throws Exception {
        var body = createdMr("mergeable").put(field, 8);
        server.expect(requestTo(ENDPOINT + "/17")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));

        var result = adapter.read("17");

        assertEquals(GitReadResult.Status.MISMATCH, result.status());
        assertNull(result.evidence());
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"project_id", "source_project_id", "target_project_id"})
    void createMrIntentDetailDoesNotCoerceTextIntoNumericIdentity(String field) throws Exception {
        var body = createdMr("mergeable").put(field, "7");
        server.expect(requestTo(ENDPOINT + "/17")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));

        var result = adapter.read("17");

        assertEquals(GitReadResult.Status.INCOMPLETE, result.status());
        assertNull(result.evidence());
        server.verify();
    }
    // End B9 independent contract.

    private static com.fasterxml.jackson.databind.node.ObjectNode mergedMr() throws Exception {
        return createdMr("mergeable").put("state", "merged").put("merge_commit_sha", "b".repeat(40));
    }

    private GitWriteResult<GitMergeRequestPort.MergeRequest> mergeResponse(
            int status, com.fasterxml.jackson.databind.JsonNode response, String frozenSha) {
        server.expect(requestTo(WRITE_ENDPOINT + "/17/merge")).andExpect(method(HttpMethod.PUT))
                .andExpect(request -> assertEquals(frozenSha, new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString())
                        .path("sha").asText()))
                .andRespond(withStatus(org.springframework.http.HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON).body(response.toString()));
        var result = adapter.merge("17", frozenSha, "merge");
        server.verify();
        return result;
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode createdMr(String readiness) throws Exception {
        return (com.fasterxml.jackson.databind.node.ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(mr(17, readiness, "running"));
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
