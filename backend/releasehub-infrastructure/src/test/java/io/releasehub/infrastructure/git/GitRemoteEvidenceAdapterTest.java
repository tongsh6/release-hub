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
