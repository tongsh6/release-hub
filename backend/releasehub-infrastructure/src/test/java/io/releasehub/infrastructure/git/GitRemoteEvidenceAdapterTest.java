package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.gitoperation.*;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.domain.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitRemoteEvidenceAdapterTest {
    private MockRestServiceServer server;
    private GitRemoteEvidenceAdapter adapter;
    @BeforeEach void setup() {
        var http=new RestTemplate();server=MockRestServiceServer.bindTo(http).build();
        var builder=mock(RestTemplateBuilder.class);when(builder.requestFactory(any(java.util.function.Supplier.class))).thenReturn(builder);when(builder.build()).thenReturn(http);
        var repos=mock(CodeRepositoryPort.class);var repo=mock(CodeRepository.class);
        when(repo.getCloneUrl()).thenReturn("https://gitlab.invalid/acme/repo.git");when(repo.getGitProvider()).thenReturn(GitProvider.GITLAB);when(repo.getGitAccessToken()).thenReturn("fixture");
        when(repos.findById(RepoId.of("repo"))).thenReturn(Optional.of(repo));
        adapter=new GitRemoteEvidenceAdapter(repos,mock(SettingsPort.class),new ObjectMapper(),builder);
        server.expect(requestTo("https://gitlab.invalid/api/v4/projects/acme%2Frepo")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":17}",MediaType.APPLICATION_JSON));
    }
    private static final String RELEASE_SHA = "a".repeat(40);

    private GitOperationJournalPort.Detail releaseDetail(String action, String target, String eventEvidence) {
        var op = new GitOperationJournalPort.Operation("operation-release", "repo", "remote", "GITLAB",
                "https://gitlab.invalid", "17", action, target, null, "tester", "UNKNOWN", 2,
                Instant.EPOCH, Instant.EPOCH, "RESPONSE_UNCONFIRMED", true);
        var events = eventEvidence == null ? List.<GitOperationJournalPort.Event>of()
                : List.of(new GitOperationJournalPort.Event("attempt", 1, "HTTP_OBSERVED", Instant.EPOCH, eventEvidence, null));
        return new GitOperationJournalPort.Detail(op, events, List.of(), false);
    }
    private GitOperationJournalPort.Detail pipelineDetail() {
        return releaseDetail("TRIGGER_PIPELINE", "{\"ref\":\"v1\",\"sha\":\"" + RELEASE_SHA + "\"}", "{\"id\":321}");
    }
    private static String pipelineBody() {
        return """
                {"id":321,"project_id":17,"ref":"v1","sha":"%s",
                 "web_url":"https://gitlab.invalid/acme/repo/-/pipelines/321","status":"success"}
                """.formatted(RELEASE_SHA);
    }
    private void pipelineRead(String body) {
        server.expect(requestTo("https://gitlab.invalid/api/v4/projects/17/pipelines/321"))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test void shouldConfirmPipelineOnlyWithFullMatchingEvidence() throws Exception {
        pipelineRead(pipelineBody());
        var result = adapter.read(pipelineDetail());
        assertEquals(GitReadResult.Status.FOUND, result.status());
        assertEquals(RELEASE_SHA, new ObjectMapper().readTree(result.evidence()).path("sha").asText());
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidPipelineBodies")
    void shouldNotConfirmPipelineFromMatchingRefAlone(String body) {
        pipelineRead(body);
        assertNotEquals(GitReadResult.Status.FOUND, adapter.read(pipelineDetail()).status());
        server.verify();
    }

    private static java.util.stream.Stream<String> invalidPipelineBodies() throws Exception {
        var json = new ObjectMapper();var cases = new java.util.ArrayList<String>();
        for (String field : List.of("id", "project_id", "sha", "web_url")) {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(pipelineBody());
            node.remove(field);cases.add(node.toString());
        }
        for (var entry : java.util.Map.of("id", "999", "project_id", "18", "sha", "b".repeat(40),
                "web_url", "not-an-absolute-url").entrySet()) {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(pipelineBody());
            if (entry.getKey().equals("id") || entry.getKey().equals("project_id")) node.put(entry.getKey(), Integer.parseInt(entry.getValue()));
            else node.put(entry.getKey(), entry.getValue());
            cases.add(node.toString());
        }
        return cases.stream();
    }

    @Test void shouldNotConfirmPipelineWithoutRecordedExpectedSha() {
        pipelineRead(pipelineBody());
        var oldIntent = releaseDetail("TRIGGER_PIPELINE", "{\"ref\":\"v1\"}", "{\"id\":321}");
        assertEquals(GitReadResult.Status.INCOMPLETE, adapter.read(oldIntent).status());
        server.verify();
    }

    @Test void shouldNotSearchForPipelineWhenNoRemoteIdWasObserved() {
        var unknown = releaseDetail("TRIGGER_PIPELINE", "{\"ref\":\"v1\",\"sha\":\"" + RELEASE_SHA + "\"}", null);
        assertEquals(GitReadResult.Status.UNSUPPORTED, adapter.read(unknown).status());
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"other", ""})
    void shouldNotConfirmTagAtExpectedShaWithDifferentName(String name) {
        server.expect(requestTo("https://gitlab.invalid/api/v4/projects/17/repository/tags/v1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"" + name + "\",\"commit\":{\"id\":\"" + RELEASE_SHA + "\"}}", MediaType.APPLICATION_JSON));
        var intent = releaseDetail("CREATE_TAG", "{\"tag_name\":\"v1\",\"ref\":\"" + RELEASE_SHA + "\"}", null);
        assertNotEquals(GitReadResult.Status.FOUND, adapter.read(intent).status());
        server.verify();
    }

    @Test void shouldConfirmOnlyExactTagNameAndSha() {
        server.expect(requestTo("https://gitlab.invalid/api/v4/projects/17/repository/tags/v1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"name\":\"v1\",\"commit\":{\"id\":\"" + RELEASE_SHA + "\"}}", MediaType.APPLICATION_JSON));
        var intent = releaseDetail("CREATE_TAG", "{\"tag_name\":\"v1\",\"ref\":\"" + RELEASE_SHA + "\"}", null);
        assertEquals(GitReadResult.Status.FOUND, adapter.read(intent).status());
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"40,false,false", "64,true,true"})
    void mergeObservationRecordsTypedMergedEvidence(int shaLength, boolean completeRequest, boolean noMergeCommit)
            throws Exception {
        var expected = mergeObservationBody(shaLength, noMergeCommit);
        var response = expected.deepCopy().put("description", "private-mr-description")
                .put("title", "private-mr-title").put("private_token", "private-fixture-token");
        response.putObject("author").put("private", "nested-secret");
        mergeObservationRead(200, response.toString());

        var result = adapter.read(releaseDetail("MERGE_MR",
                mergeObservationTarget(shaLength, completeRequest).toString(), null));

        assertEquals(GitReadResult.Status.FOUND, result.status());
        assertEquals(expected, new ObjectMapper().readTree(result.evidence()));
        server.verify();
    }

    @Test
    void mergeObservationKeepsRetargetedMrAsObservedFactOnly() throws Exception {
        var target = mergeObservationTarget(40, true);
        assertFalse(target.has("source_branch"));
        assertFalse(target.has("target_branch"));
        var expected = mergeObservationBody(40, false).put("target_branch", "release/retargeted");
        mergeObservationRead(200, expected.toString());
        var original = releaseDetail("MERGE_MR", target.toString(), null);

        var result = adapter.read(original);

        assertEquals(GitReadResult.Status.FOUND, result.status());
        assertEquals(expected, new ObjectMapper().readTree(result.evidence()));
        assertEquals(target.toString(), original.operation().targetJson());
        assertEquals("UNKNOWN", original.operation().state());
        assertTrue(original.operation().blocked());
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("mergeObservationResponseCases")
    void mergeObservationRejectsUnprovableMergedResponse(String label, int status, String body,
            GitReadResult.Status expectedStatus, String expectedEvidence) throws Exception {
        mergeObservationRead(status, body);

        var result = adapter.read(releaseDetail("MERGE_MR", mergeObservationTarget(40, false).toString(), null));

        assertEquals(expectedStatus, result.status(), label);
        assertEquals(new ObjectMapper().readTree(expectedEvidence),
                new ObjectMapper().readTree(result.evidence()), label);
        assertFalse(result.evidence().contains("private-fixture-token"), label);
        assertFalse(result.evidence().contains("nested-secret"), label);
        server.verify();
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> mergeObservationResponseCases() {
        var cases = new java.util.ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for (String label : List.of("WRONG_IID", "MISSING_PROJECT_ID", "FOREIGN_SOURCE_PROJECT",
                "TEXT_TARGET_PROJECT", "SOURCE_SHA_MOVED", "STILL_OPENED", "MISSING_SOURCE_BRANCH",
                "EMPTY_TARGET_BRANCH", "RELATIVE_URL", "MISSING_MERGE_SHA", "NESTED_MERGE_SHA",
                "EMPTY_OBJECT", "EMPTY_BODY", "STATUS_201", "NON_OBJECT")) {
            var response = mergeObservationBody(40, false);
            switch (label) {
                case "WRONG_IID" -> response.put("iid", 8);
                case "MISSING_PROJECT_ID" -> response.remove("project_id");
                case "FOREIGN_SOURCE_PROJECT" -> response.put("source_project_id", 18);
                case "TEXT_TARGET_PROJECT" -> response.put("target_project_id", "17");
                case "SOURCE_SHA_MOVED" -> response.put("sha", "b".repeat(40));
                case "STILL_OPENED" -> response.put("state", "opened");
                case "MISSING_SOURCE_BRANCH" -> response.remove("source_branch");
                case "EMPTY_TARGET_BRANCH" -> response.put("target_branch", "");
                case "RELATIVE_URL" -> response.put("web_url", "/relative/mr/7");
                case "MISSING_MERGE_SHA" -> response.remove("merge_commit_sha");
                case "NESTED_MERGE_SHA" -> response.putObject("merge_commit_sha").put("private", "nested-secret");
                case "EMPTY_OBJECT" -> response.removeAll();
                case "EMPTY_BODY", "STATUS_201", "NON_OBJECT" -> { }
                default -> throw new AssertionError(label);
            }
            var expectedEvidence = response.deepCopy();
            if ("NESTED_MERGE_SHA".equals(label)) expectedEvidence.remove("merge_commit_sha");
            int status = "STATUS_201".equals(label) ? 201 : 200;
            GitReadResult.Status expectedStatus = GitReadResult.Status.MISMATCH;
            String body;
            if ("EMPTY_BODY".equals(label) || "NON_OBJECT".equals(label)) {
                body = "EMPTY_BODY".equals(label) ? "" : "[{\"private\":\"nested-secret\"}]";
                expectedStatus = GitReadResult.Status.INCOMPLETE;
                expectedEvidence.removeAll();
            } else {
                if (!"EMPTY_OBJECT".equals(label)) {
                    response.put("description", "private-mr-description").put("private_token", "private-fixture-token");
                    response.putObject("author").put("private", "nested-secret");
                }
                body = response.toString();
            }
            cases.add(org.junit.jupiter.params.provider.Arguments.of(label, status, body,
                    expectedStatus, expectedEvidence.toString()));
        }
        return cases.stream();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "MISSING_SHA", "INVALID_SHA", "WRONG_METHOD", "ZERO_IID", "QUERY_IN_SUFFIX", "NON_TEXT_SUFFIX"})
    void mergeObservationRejectsUnusableOriginalLocator(String invalid) {
        var target = mergeObservationTarget(40, false);
        switch (invalid) {
            case "MISSING_SHA" -> target.remove("sha");
            case "INVALID_SHA" -> target.put("sha", "not-a-commit");
            case "WRONG_METHOD" -> target.put("method", "POST");
            case "ZERO_IID" -> target.put("suffix", "/merge_requests/0/merge");
            case "QUERY_IN_SUFFIX" -> target.put("suffix", "/merge_requests/7/merge?other=1");
            case "NON_TEXT_SUFFIX" -> target.put("suffix", 7);
            default -> throw new AssertionError(invalid);
        }
        // Identity confirmation may occur before or after locator validation. No MR read is authorized.
        server.reset();
        server.expect(org.springframework.test.web.client.ExpectedCount.between(0, 1),
                        requestTo("https://gitlab.invalid/api/v4/projects/acme%2Frepo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":17}", MediaType.APPLICATION_JSON));

        var result = adapter.read(releaseDetail("MERGE_MR", target.toString(), null));

        assertEquals(GitReadResult.Status.INCOMPLETE, result.status(), invalid);
        assertEquals("{}", result.evidence(), invalid);
        server.verify();
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode mergeObservationTarget(
            int shaLength, boolean completeRequest) {
        String expectedSha = shaLength == 64 ? "aA".repeat(32) : RELEASE_SHA;
        var target = new ObjectMapper().createObjectNode().put("method", "PUT")
                .put("suffix", "/merge_requests/7/merge").put("sha", expectedSha);
        if (completeRequest) target.put("merge_commit_message", "Merge feature into main")
                .put("should_remove_source_branch", false);
        return target;
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode mergeObservationBody(
            int shaLength, boolean noMergeCommit) {
        var body = new ObjectMapper().createObjectNode()
                .put("project_id", 17).put("source_project_id", 17).put("target_project_id", 17).put("iid", 7)
                .put("source_branch", "feature").put("target_branch", "main")
                .put("sha", "a".repeat(shaLength)).put("state", "merged")
                .put("web_url", "https://gitlab.invalid/acme/repo/-/merge_requests/7");
        if (noMergeCommit) body.putNull("merge_commit_sha");
        else body.put("merge_commit_sha", "c".repeat(shaLength));
        return body;
    }

    private void mergeObservationRead(int status, String body) {
        server.expect(requestTo("https://gitlab.invalid/api/v4/projects/17/merge_requests/7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private GitOperationJournalPort.Detail detail() {
        var op=new GitOperationJournalPort.Operation("operation-1","repo","remote","GITLAB","https://gitlab.invalid","17","CREATE_MR",
                "{\"source_branch\":\"feature\",\"target_branch\":\"main\"}",null,"tester","UNKNOWN",2,Instant.EPOCH,Instant.EPOCH,"RESPONSE_LOST",true);
        return new GitOperationJournalPort.Detail(op,List.of(),List.of(),false);
    }
    private String mr(){return "{\"iid\":3,\"description\":\"ReleaseHub-Operation: operation-1\",\"source_branch\":\"feature\",\"target_branch\":\"main\"}";}
    private String url(){return "https://gitlab.invalid/api/v4/projects/17/merge_requests?scope=all&state=all&per_page=100&page=1";}
    @Test void shouldFindUniqueCorrelatedMrUsingOnlyGet() {
        server.expect(requestTo(url())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("["+mr()+"]",MediaType.APPLICATION_JSON).header("X-Next-Page",""));
        assertEquals(GitReadResult.Status.FOUND,adapter.read(detail()).status());server.verify();
    }
    @Test void shouldNotTreatMissingPaginationAsComplete() {
        server.expect(requestTo(url())).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("["+mr()+"]",MediaType.APPLICATION_JSON));
        assertEquals(GitReadResult.Status.INCOMPLETE,adapter.read(detail()).status());server.verify();
    }
    @Test void shouldKeepAccessFailureDistinctFromNotFound() {
        server.expect(requestTo(url())).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertEquals(GitReadResult.Status.FORBIDDEN,adapter.read(detail()).status());server.verify();
    }
    @Test void shouldNotAcceptMultipleCorrelationMatches() {
        server.expect(requestTo(url())).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("["+mr()+","+mr()+"]",MediaType.APPLICATION_JSON).header("X-Next-Page",""));
        assertEquals(GitReadResult.Status.MISMATCH,adapter.read(detail()).status());server.verify();
    }
}
