package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.gitoperation.GitOperationContext;
import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.ActionType;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GitTaskIdentityTest {
    private static final String BASE = "https://gitlab.test";
    private static final String URL = BASE + "/acme/repo.git";
    private static final String SHA = "a".repeat(40);
    private final AtomicReference<String> project = new AtomicReference<>("7");
    private final AtomicReference<String> localRepo = new AtomicReference<>("repo");
    private final AtomicInteger writes = new AtomicInteger();
    private final ArrayList<String> reads = new ArrayList<>();
    private final GitRemoteIdentityResolver identities = mock(GitRemoteIdentityResolver.class);
    private final GitOperationJournalPort journal = mock(GitOperationJournalPort.class);
    private final RestTemplate http = new RestTemplate();
    private final GitLabGitBranchAdapter adapter = new GitLabGitBranchAdapter(new RestTemplateBuilder(), identities);
    private MockRestServiceServer server;
    private boolean missing;

    @BeforeEach void setup() throws Exception {
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(identities.resolve(any(), any())).thenAnswer(call -> {
            var endpoint = GitRemoteIdentityResolver.parse(call.getArgument(0, URI.class));
            return new GitRemoteIdentityResolver.Identity(localRepo.get(), "mapping",
                    GitOperationJournalPort.remoteKey("GITLAB", BASE, project.get()),
                    "GITLAB", BASE, project.get(), endpoint);
        });
        when(identities.metadata(any(), anyString(), any(), any())).thenReturn(
                new ObjectMapper().readTree("{\"id\":\"" + SHA + "\"}"));
        when(journal.begin(any())).thenAnswer(call -> call.getArgument(0, GitOperationJournalPort.Command.class).operationId());
        http.getInterceptors().add(new GitWriteHttpInterceptor(new GitSafetyProperties(), new GitSafetySessions(journal),
                journal, identities, new ObjectMapper(), transactions));
        adapter.setRestTemplate(http);
        server = MockRestServiceServer.bindTo(http).build();
        server.expect(ExpectedCount.manyTimes(), request -> {}).andRespond(request -> {
            if (request.getMethod() == HttpMethod.GET) {
                reads.add(request.getURI().toString());
                if (missing) return withStatus(HttpStatus.NOT_FOUND).createResponse(request);
                return withSuccess("{\"name\":\"feature/IT-1\",\"commit\":{\"id\":\"" + SHA + "\"}}",
                        MediaType.APPLICATION_JSON).createResponse(request);
            }
            writes.incrementAndGet();
            return withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"name\":\"release/RW-1\",\"commit\":{\"id\":\"" + SHA + "\"}}").createResponse(request);
        });
    }

    @Test void firstExistingBranchReadMustCarryAndBindTheNumericProject() {
        try (var context = GitOperationContext.withTask(claim("repo", ActionType.ENSURE_FEATURE, Map.of()))) {
            var result = adapter.getBranchStatus(URL, "fixture-token", "feature/IT-1");
            assertTrue(result.exists());
            assertEquals("7", result.projectId());
            assertEquals("7", GitOperationContext.expectedProjectId());
            assertEquals(BASE + "/api/v4/projects/7/repository/branches/feature%2FIT-1", reads.get(0));
            project.set("8");
            var error = assertThrows(BusinessException.class,
                    () -> adapter.getBranchStatus(URL, "fixture-token", "feature/IT-1"));
            assertEquals(ErrorCode.GIT_IDENTITY_UNAVAILABLE, error.getErrorCode());
        }
        assertEquals(1, reads.size());
        assertEquals(0, writes.get());
        verifyNoInteractions(journal);
    }

    @Test void missingBranchReadMustPreventFirstWriteAfterNamespaceReuse() {
        missing = true;
        try (var context = GitOperationContext.withTask(claim("repo", ActionType.ENSURE_FEATURE, Map.of()))) {
            assertFalse(adapter.getBranchStatus(URL, "fixture-token", "feature/IT-1").exists());
            project.set("8");
            var error = assertThrows(BusinessException.class,
                    () -> adapter.createBranch(URL, "fixture-token", "release/RW-1", "main"));
            assertEquals(ErrorCode.GIT_IDENTITY_UNAVAILABLE, error.getErrorCode());
        }
        assertEquals(0, writes.get());
        verify(journal, never()).begin(any());
    }

    @Test void persistedReadIdentityMustPreventTheNextTaskFromWritingAnotherProject() {
        project.set("8");
        try (var context = GitOperationContext.withTask(claim("repo", ActionType.ENSURE_RELEASE, Map.of("git.projectId", "7")))) {
            var error = assertThrows(BusinessException.class,
                    () -> adapter.createBranch(URL, "fixture-token", "release/RW-1", "main"));
            assertEquals(ErrorCode.GIT_IDENTITY_UNAVAILABLE, error.getErrorCode());
        }
        assertEquals(0, writes.get());
        verify(journal, never()).begin(any());
    }

    @Test void separateRepositoryTasksMayBindDifferentProjects() {
        try (var context = GitOperationContext.withTask(claim("repo", ActionType.ENSURE_FEATURE, Map.of()))) {
            assertEquals("7", adapter.getBranchStatus(URL, "fixture-token", "feature/IT-1").projectId());
        }
        project.set("8");
        localRepo.set("repo-8");
        try (var context = GitOperationContext.withTask(claim("repo-8", ActionType.ENSURE_FEATURE, Map.of()))) {
            assertEquals("8", adapter.getBranchStatus(URL, "fixture-token", "feature/IT-1").projectId());
            assertEquals("8", GitOperationContext.expectedProjectId());
        }
        assertNull(GitOperationContext.expectedProjectId());
        assertEquals(0, writes.get());
        verifyNoInteractions(journal);
    }

    private RunPort.TaskClaim claim(String repoId, ActionType action, Map<String, String> facts) {
        return new RunPort.TaskClaim(new RunPort.TaskFence(UUID.randomUUID().toString(), UUID.randomUUID().toString()),
                "run", "item", RepoId.of(repoId), action, Instant.parse("2026-11-21T00:00:00Z"), "admitted-user", facts);
    }
}
