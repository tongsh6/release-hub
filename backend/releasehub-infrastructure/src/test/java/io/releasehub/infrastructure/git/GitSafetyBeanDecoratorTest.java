package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.gitoperation.GitWriteResult;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.domain.run.MergeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitSafetyBeanDecoratorTest {
    private static final String CLONE = "https://gitlab.test/acme/repo.git";
    private static final String PROJECT = "https://gitlab.test/api/v4/projects/acme%2Frepo";
    private static final String SOURCE = "feature/IT-1";
    private static final String TARGET = "release/RW-1";
    private static final String SHA = "a".repeat(40);
    private final GitOperationJournalPort journal = mock(GitOperationJournalPort.class);
    private MockRestServiceServer server;
    private GitBranchPort adapter;

    @BeforeEach void setup() {
        var properties = new GitSafetyProperties();
        properties.setEnabled(true);
        var sessions = new GitSafetySessions(journal);
        var identities = mock(GitRemoteIdentityResolver.class);
        var endpoint = new GitRemoteIdentityResolver.Endpoint("GITLAB", "https://gitlab.test",
                "acme/repo", PROJECT, "/merge_requests");
        when(identities.resolve(any(), any())).thenReturn(new GitRemoteIdentityResolver.Identity(
                "repo", "mapping", "b".repeat(64), "GITLAB", "https://gitlab.test", "17", endpoint));
        when(journal.begin(any())).thenAnswer(invocation ->
                ((GitOperationJournalPort.Command) invocation.getArgument(0)).operationId());
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var http = new RestTemplate();
        http.getInterceptors().add(new GitWriteHttpInterceptor(properties, sessions, journal,
                identities, new ObjectMapper(), transactions));
        server = MockRestServiceServer.bindTo(http).build();
        var target = new GitLabGitBranchAdapter(new RestTemplateBuilder());
        target.setRestTemplate(http);
        var decorator = new GitSafetyBeanDecorator(provider(properties), provider(sessions), provider(journal),
                provider(mock(CodeRepositoryPort.class)), provider(transactions));
        adapter = (GitBranchPort) decorator.postProcessAfterInitialization(target, "gitLabFixture");
    }

    @Test void shouldKeepAppliedHttpGuardWhenWorkflowNormallyReturnsUnknown() {
        sourceRead();
        openedRead();
        server.expect(requestTo(PROJECT + "/repository/commits/" + SHA + "/refs?type=branch&per_page=100&page=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        // The HTTP response proves an MR was created, but cannot prove its business identity.
        server.expect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"iid\":7}", MediaType.APPLICATION_JSON));

        var result = adapter.mergeBranch(CLONE, "token", SOURCE, TARGET, "merge");

        assertEquals(MergeStatus.UNKNOWN, result.status());
        verify(journal).finish(anyString(), eq(GitWriteResult.Outcome.APPLIED), anyString(), isNull());
        verify(journal).completeSession(anyString(), eq(false));
        verify(journal, never()).completeSession(anyString(), eq(true));
        server.verify();
    }

    @Test void shouldReleaseConfirmedBlockedWorkflowWithoutFreezingRepository() {
        sourceRead();
        openedRead();
        server.expect(requestTo(PROJECT + "/repository/commits/" + SHA + "/refs?type=branch&per_page=100&page=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.POST)).andRespond(withSuccess("""
                {"iid":7,"project_id":17,"source_project_id":17,"target_project_id":17,
                 "source_branch":"feature/IT-1","target_branch":"release/RW-1","state":"opened",
                 "web_url":"https://gitlab.test/acme/repo/-/merge_requests/7","sha":"%s",
                 "detailed_merge_status":"not_approved"}
                """.formatted(SHA), MediaType.APPLICATION_JSON));

        var result = adapter.mergeBranch(CLONE, "token", SOURCE, TARGET, "merge");

        assertEquals(MergeStatus.BLOCKED, result.status());
        assertEquals(GitBranchPort.MergeBlockReason.APPROVAL_REQUIRED, result.mrEvidence().blockReason());
        verify(journal).finish(anyString(), eq(GitWriteResult.Outcome.APPLIED), anyString(), isNull());
        verify(journal).completeSession(anyString(), eq(true));
        verify(journal, never()).completeSession(anyString(), eq(false));
        server.verify();
    }

    @Test void shouldNotCreateGuardForUnknownReadWithoutAnyWrite() {
        sourceRead();
        server.expect(requestTo(openedUri())).andRespond(withServerError());

        var result = adapter.mergeBranch(CLONE, "token", SOURCE, TARGET, "merge");

        assertEquals(MergeStatus.UNKNOWN, result.status());
        verifyNoInteractions(journal);
        server.verify();
    }

    private void sourceRead() {
        server.expect(requestTo(PROJECT)).andRespond(withSuccess("{\"id\":17}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(PROJECT + "/repository/branches/feature%2FIT-1"))
                .andRespond(withSuccess("{\"name\":\"feature/IT-1\",\"commit\":{\"id\":\"" + SHA + "\"}}",
                        MediaType.APPLICATION_JSON));
    }

    private void openedRead() {
        server.expect(requestTo(openedUri())).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    }

    private static String openedUri() {
        return PROJECT + "/merge_requests?scope=all&state=opened&source_branch=feature%2FIT-1&target_branch=release%2FRW-1&per_page=100&page=1";
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> result = mock(ObjectProvider.class);
        when(result.getObject()).thenReturn(bean);
        return result;
    }
}
