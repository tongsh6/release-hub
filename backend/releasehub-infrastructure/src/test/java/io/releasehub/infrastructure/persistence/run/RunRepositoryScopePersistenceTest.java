package io.releasehub.infrastructure.persistence.run;

import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunItemId;
import io.releasehub.domain.run.RunType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunRepositoryScopePersistenceTest {

    @Test
    void rejectsNonPositiveClaimLeaseAtBeanConstruction() {
        for (long lease : List.of(0L, -1L)) {
            try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                        "claim-lease-fixture", Map.of("releasehub.run.worker.claim-lease-ms", Long.toString(lease))));
                context.registerBean(RunJpaRepository.class, () -> mock(RunJpaRepository.class));
                context.registerBean(RunTaskJpaRepository.class, () -> mock(RunTaskJpaRepository.class));
                context.registerBean(jakarta.persistence.EntityManager.class, () -> mock(jakarta.persistence.EntityManager.class));
                context.registerBean(java.time.Clock.class, java.time.Clock::systemUTC);
                context.registerBean(io.releasehub.application.window.WindowIterationPort.class,
                        () -> mock(io.releasehub.application.window.WindowIterationPort.class));
                context.registerBean(io.releasehub.application.gitoperation.GitOperationJournalPort.class,
                        () -> mock(io.releasehub.application.gitoperation.GitOperationJournalPort.class));
                context.registerBean(RunJpaPersistenceAdapter.class);
                var failure = assertThrows(org.springframework.beans.factory.BeanCreationException.class, context::refresh);
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                assertInstanceOf(IllegalArgumentException.class, cause);
                assertTrue(cause.getMessage().contains("claim-lease-ms"));
            }
        }
    }

    @Test
    void savingARepositoryItemPreservesNullIterationAndFrozenParticipantMetadata() {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        Run run = Run.start(RunType.WINDOW_CLEANUP, "alice", now);
        run.addItem(RunItem.rehydrate(RunItemId.of("cleanup-item"), "RW", RepoId.of("repo"), null,
                1, 0, null, List.of(), Map.of("plan.participatingIterationKeys", "[\"ITER-A\",\"ITER-B\"]"), now, now, 0));
        RunJpaRepository repository = mock(RunJpaRepository.class);
        var adapter = new RunJpaPersistenceAdapter(repository, mock(RunTaskJpaRepository.class), mock(jakarta.persistence.EntityManager.class),
                java.time.Clock.systemUTC(), mock(io.releasehub.application.window.WindowIterationPort.class),
                mock(io.releasehub.application.gitoperation.GitOperationJournalPort.class), 3_600_000L);

        assertDoesNotThrow(() -> adapter.save(run));

        var captor = ArgumentCaptor.forClass(RunJpaEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(1, captor.getValue().getItems().size());
        var item = captor.getValue().getItems().get(0);
        assertNull(item.getIterationKey());
        assertEquals("repo", item.getRepoId());
        assertEquals("[\"ITER-A\",\"ITER-B\"]", item.getMetadata().get("plan.participatingIterationKeys"));
    }

    @Test
    void readingARepositoryItemDoesNotInventAnIterationKey() {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        var entity = new RunJpaEntity();
        entity.setId("cleanup-run");
        entity.setRunType("WINDOW_CLEANUP");
        entity.setOperator("alice");
        entity.setStartedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setExecutionStatus("RUNNING");
        entity.setExecutionVersion(2);
        var item = new RunItemJpaEntity();
        item.setId("cleanup-item");
        item.setRun(entity);
        item.setWindowKey("RW");
        item.setRepoId("repo");
        item.setIterationKey(null);
        item.setPlannedOrder(1);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        entity.setItems(List.of(item));
        var repository = mock(RunJpaRepository.class);
        when(repository.findById("cleanup-run")).thenReturn(Optional.of(entity));

        Run restored = new RunJpaPersistenceAdapter(repository, mock(RunTaskJpaRepository.class), mock(jakarta.persistence.EntityManager.class),
                java.time.Clock.systemUTC(), mock(io.releasehub.application.window.WindowIterationPort.class),
                mock(io.releasehub.application.gitoperation.GitOperationJournalPort.class), 3_600_000L).findById("cleanup-run").orElseThrow();

        assertEquals(1, restored.getItems().size());
        var restoredItem = restored.getItems().get(0);
        assertNull(restoredItem.getIterationKey());
        assertEquals("RW", restoredItem.getWindowKey());
        assertEquals("repo", restoredItem.getRepo().value());
    }
}
