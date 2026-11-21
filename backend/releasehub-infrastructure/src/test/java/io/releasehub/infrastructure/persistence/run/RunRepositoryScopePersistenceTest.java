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
    void savingARepositoryItemPreservesNullIterationAndFrozenParticipantMetadata() {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        Run run = Run.start(RunType.WINDOW_CLEANUP, "alice", now);
        run.addItem(RunItem.rehydrate(RunItemId.of("cleanup-item"), "RW", RepoId.of("repo"), null,
                1, 0, null, List.of(), Map.of("plan.participatingIterationKeys", "[\"ITER-A\",\"ITER-B\"]"), now, now, 0));
        RunJpaRepository repository = mock(RunJpaRepository.class);
        var adapter = new RunJpaPersistenceAdapter(repository);

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

        Run restored = new RunJpaPersistenceAdapter(repository).findById("cleanup-run").orElseThrow();

        assertEquals(1, restored.getItems().size());
        var restoredItem = restored.getItems().get(0);
        assertNull(restoredItem.getIterationKey());
        assertEquals("RW", restoredItem.getWindowKey());
        assertEquals("repo", restoredItem.getRepo().value());
    }
}
