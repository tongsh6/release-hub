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
    private static final String PROJECT = "https://gitlab.test/api/v4/projects/17";
    private static final String SOURCE = "feature/IT-1";
    private static final String TARGET = "release/RW-1";
    private static final String SHA = "a".repeat(40);
    private final GitOperationJournalPort journal = mock(GitOperationJournalPort.class);
    private MockRestServiceServer server;
    private GitBranchPort adapter;
    private GitLabGitBranchAdapter target;

    @BeforeEach void setup() {
        var properties = new GitSafetyProperties();
        properties.setEnabled(true);
        var sessions = new GitSafetySessions(journal);
        var identities = mock(GitRemoteIdentityResolver.class);
        when(identities.resolve(any(), any())).thenAnswer(invocation -> new GitRemoteIdentityResolver.Identity(
                "repo", "mapping", "b".repeat(64), "GITLAB", "https://gitlab.test", "17",
                GitRemoteIdentityResolver.parse(invocation.getArgument(0))));
        when(journal.begin(any())).thenAnswer(invocation ->
                ((GitOperationJournalPort.Command) invocation.getArgument(0)).operationId());
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var http = new RestTemplate();
        http.getInterceptors().add(new GitWriteHttpInterceptor(properties, sessions, journal,
                identities, new ObjectMapper(), transactions));
        server = MockRestServiceServer.bindTo(http).build();
        target = spy(new GitLabGitBranchAdapter(new RestTemplateBuilder(), identities));
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
        // Creation is confirmed; the later original-IID read leaves the business outcome unknown.
        server.expect(requestTo(PROJECT + "/merge_requests")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body(openedMrBody("mergeable")));
        server.expect(requestTo(PROJECT + "/repository/commits/" + SHA + "/refs?type=branch&per_page=100&page=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(PROJECT + "/merge_requests/7")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

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
        String blockedMr = openedMrBody("not_approved");
        server.expect(requestTo(PROJECT + "/merge_requests")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body(blockedMr));
        server.expect(requestTo(PROJECT + "/repository/commits/" + SHA + "/refs?type=branch&per_page=100&page=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(PROJECT + "/merge_requests/7"))
                .andRespond(withSuccess(blockedMr, MediaType.APPLICATION_JSON));

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

    @Test void shouldKeepAppliedHttpGuardWhenTypedWriteNormallyReturnsUnknown() {
        server.expect(requestTo("https://gitlab.test/api/v4/projects/17/pipeline?ref=v1"))
                .andRespond(withSuccess(pipelineBody(), MediaType.APPLICATION_JSON));
        doAnswer(invocation -> {
            invocation.callRealMethod();
            return GitWriteResult.unknown("PIPELINE_RESPONSE_UNCONFIRMED");
        }).when(target).triggerPipeline(CLONE, "token", "17", "v1", SHA);

        var result = adapter.triggerPipeline(CLONE, "token", "17", "v1", SHA);

        assertEquals(GitWriteResult.Outcome.UNKNOWN, result.outcome());
        verify(journal).finish(anyString(), eq(GitWriteResult.Outcome.APPLIED), anyString(), isNull());
        verify(journal).completeSession(anyString(), eq(false));
        verify(journal, never()).completeSession(anyString(), eq(true));
        server.verify();
    }

    @Test void shouldReleaseConfirmedRejectedTypedWrite() {
        server.expect(requestTo("https://gitlab.test/api/v4/projects/17/pipeline?ref=v1"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"sensitive fixture\"}"));

        var result = adapter.triggerPipeline(CLONE, "token", "17", "v1", SHA);

        assertEquals(GitWriteResult.Outcome.REJECTED, result.outcome());
        assertEquals("HTTP_403", result.reason());
        verify(journal).finish(anyString(), eq(GitWriteResult.Outcome.REJECTED), anyString(), isNull());
        verify(journal).completeSession(anyString(), eq(true));
        verify(journal, never()).completeSession(anyString(), eq(false));
        server.verify();
    }

    @Test void shouldNotCreateGuardForTypedUnknownWithoutDispatch() {
        doReturn(GitWriteResult.unknown("NOT_DISPATCHED")).when(target)
                .triggerPipeline(CLONE, "token", "17", "v1", SHA);

        assertEquals(GitWriteResult.Outcome.UNKNOWN,
                adapter.triggerPipeline(CLONE, "token", "17", "v1", SHA).outcome());

        verifyNoInteractions(journal);
        server.verify();
    }

    private static String openedMrBody(String readiness) {
        return """
                {"iid":7,"project_id":17,"source_project_id":17,"target_project_id":17,
                 "source_branch":"feature/IT-1","target_branch":"release/RW-1","state":"opened",
                 "web_url":"https://gitlab.test/acme/repo/-/merge_requests/7","sha":"%s",
                 "detailed_merge_status":"%s"}
                """.formatted(SHA, readiness);
    }

    private static String pipelineBody() {
        return """
                {"id":321,"project_id":17,"ref":"v1","sha":"%s",
                 "web_url":"https://gitlab.test/acme/repo/-/pipelines/321"}
                """.formatted(SHA);
    }

    private void sourceRead() {
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
