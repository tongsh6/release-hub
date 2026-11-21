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
