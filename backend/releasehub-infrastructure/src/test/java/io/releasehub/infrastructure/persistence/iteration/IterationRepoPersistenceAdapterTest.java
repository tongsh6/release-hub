package io.releasehub.infrastructure.persistence.iteration;

import io.releasehub.domain.iteration.BranchCreationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IterationRepoPersistenceAdapterTest {

    @Mock
    private IterationRepoJpaRepository repository;

    private IterationRepoPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new IterationRepoPersistenceAdapter(repository);
    }

    @Test
    void shouldPersistBranchCreationModeWhenSavingVersionInfo() {
        Instant syncedAt = Instant.parse("2026-05-21T10:00:00Z");
        when(repository.findById(new IterationRepoId("ITER-1", "repo-1"))).thenReturn(Optional.empty());

        adapter.saveWithVersion(
                "ITER-1",
                "repo-1",
                "1.0.0",
                "1.1.0-SNAPSHOT",
                "1.1.0",
                "feature/custom",
                "SYSTEM",
                syncedAt,
                BranchCreationMode.NAMED
        );

        ArgumentCaptor<IterationRepoJpaEntity> captor = ArgumentCaptor.forClass(IterationRepoJpaEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("NAMED", captor.getValue().getBranchCreationMode());
    }

    @Test
    void shouldExposeBranchCreationModeWhenReadingVersionInfo() {
        IterationRepoJpaEntity entity = new IterationRepoJpaEntity(
                new IterationRepoId("ITER-1", "repo-1"),
                "1.0.0",
                "1.1.0-SNAPSHOT",
                "1.1.0",
                "feature/existing",
                "SYSTEM",
                Instant.parse("2026-05-21T10:00:00Z"),
                "EXISTING"
        );
        when(repository.findById(new IterationRepoId("ITER-1", "repo-1"))).thenReturn(Optional.of(entity));

        var versionInfo = adapter.getVersionInfo("ITER-1", "repo-1");

        assertTrue(versionInfo.isPresent());
        assertEquals(BranchCreationMode.EXISTING, versionInfo.get().getBranchCreationMode());
    }
}
