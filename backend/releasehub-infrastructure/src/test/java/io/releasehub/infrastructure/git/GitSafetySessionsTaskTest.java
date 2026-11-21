package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitOperationContext;
import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.ActionType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitSafetySessionsTaskTest {
    private final GitOperationJournalPort journal = mock(GitOperationJournalPort.class);
    private final GitSafetySessions sessions = new GitSafetySessions(journal);
    private final RunPort.TaskClaim claim = new RunPort.TaskClaim(
            new RunPort.TaskFence("e06f7c88-69ea-4ef7-a6f8-ac095f63ce59", "8d5f3932-7fe0-45be-a04c-5fb52bd1890b"),
            "run", "item", RepoId.of("repo"), ActionType.ENSURE_MR, Instant.parse("2026-11-21T00:00:00Z"),
            "admitted-operator", Map.of());

    @Test void aReturnedAdapterCannotAcknowledgeTheTaskBeforeItsResultCommits() {
        try (var context = GitOperationContext.withTask(claim)) {
            try (var scope = sessions.open()) { scope.id(); }
        }
        verifyNoInteractions(journal);
    }

    @Test void anUnknownTaskScopeStillLeavesDurableAcknowledgementToTheResultTransaction() {
        try (var context = GitOperationContext.withTask(claim)) {
            try (var scope = sessions.open()) {
                scope.id();
                scope.failed();
            }
        }
        verifyNoInteractions(journal);
    }

    @Test void taskSessionIdentityComesFromThePersistedClaim() {
        try (var context = GitOperationContext.withTask(claim);
             var scope = sessions.open()) {
            assertEquals(claim.fence().claimToken(), scope.id());
        }
    }

    @Test void nonTaskConsumerKeepsItsExistingScopeAcknowledgement() {
        String session;
        try (var context = GitOperationContext.withRun("cleanup")) {
            try (var scope = sessions.open()) { session = scope.id(); }
        }
        verify(journal).completeSession(session, true);
    }

    @Test void taskProvenanceRestoresThePreviousRunAndCannotBeSilentlyReplaced() {
        try (var outer = GitOperationContext.withRun("outer")) {
            try (var context = GitOperationContext.withTask(claim)) {
                assertEquals("run", GitOperationContext.runId());
                assertEquals("admitted-operator", GitOperationContext.operator());
                assertEquals(claim.fence(), GitOperationContext.taskFence());
                assertThrows(IllegalStateException.class, () -> GitOperationContext.withRun("different"));
            }
            assertEquals("outer", GitOperationContext.runId());
            assertNull(GitOperationContext.taskFence());
            assertNull(GitOperationContext.operator());
        }
        assertNull(GitOperationContext.runId());
    }
}
