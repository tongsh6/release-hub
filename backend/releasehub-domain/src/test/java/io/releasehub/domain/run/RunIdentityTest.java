package io.releasehub.domain.run;

import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class RunIdentityTest {
    @Test
    void allocatesIndependentRunsAndItemsForTheSameInstantAndScope() {
        Instant now = Instant.parse("2026-10-05T00:00:00Z");
        var runs = new HashSet<RunId>();
        var items = new HashSet<RunItemId>();
        for (int i = 0; i < 1000; i++) {
            runs.add(Run.start(RunType.WINDOW_ORCHESTRATION, "tester", now).getId());
            items.add(RunItem.create("RW", RepoId.of("repo"), IterationKey.of("ITER"), 1, now).getId());
            items.add(RunItem.createForRepository("RW", RepoId.of("repo"), 1, now).getId());
        }
        assertThat(runs).hasSize(1000);
        assertThat(items).hasSize(2000);
    }

    @Test
    void repositoryItemsHaveIndependentIdsWithoutBorrowingAnIteration() {
        Instant now = Instant.parse("2026-11-21T00:00:00Z");
        var first = RunItem.createForRepository("RW", RepoId.of("repo"), 1, now);
        var second = RunItem.createForRepository("RW", RepoId.of("repo"), 1, now);
        assertThat(first.getIterationKey()).isNull();
        assertThat(first.getWindowKey()).isEqualTo("RW");
        assertThat(first.getRepo()).isEqualTo(RepoId.of("repo"));
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void preservesLegacyIdentifiersWhenReading() {
        assertThat(RunId.of("WINDOW_ORCHESTRATION::123").value()).isEqualTo("WINDOW_ORCHESTRATION::123");
        assertThat(RunItemId.of("RW::repo::ITER").value()).isEqualTo("RW::repo::ITER");
    }
}
