package io.releasehub.domain.run;

import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RunStatusTest {
    private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");

    @Test
    void acceptedRunIsExplicitlyRunning() {
        assertThat(Run.start(RunType.WINDOW_ORCHESTRATION, "tester", NOW).getExecutionStatus())
                .isNotNull().extracting(Enum::name).isEqualTo("RUNNING");
    }

    @Test
    void allRepositoriesMergedOrAlreadyMergedAreDone() {
        Run run = run();
        add(run, "r1", "i1", RunItemResult.MERGED);
        add(run, "r1", "i2", RunItemResult.ALREADY_MERGED);
        add(run, "r2", "i1", RunItemResult.MERGED);
        run.finish(NOW);
        assertStatus(run, "DONE");
    }

    @Test
    void oneBlockedRepositoryDoesNotEraseAnotherRepositoriesSuccess() {
        Run run = run();
        add(run, "r1", "i1", RunItemResult.MERGE_BLOCKED);
        add(run, "r1", "i2", RunItemResult.SKIPPED_DUE_TO_BLOCK);
        add(run, "r2", "i1", RunItemResult.MERGED);
        run.finish(NOW);
        assertStatus(run, "PARTIAL_FAILED");
    }

    @Test
    void allRepositoriesWithUnfinishedMergesAreFailed() {
        Run run = run();
        add(run, "r1", "i1", RunItemResult.MERGED);
        add(run, "r1", "i2", RunItemResult.MERGE_BLOCKED);
        add(run, "r2", "i1", RunItemResult.FAILED);
        run.finish(NOW);
        assertStatus(run, "FAILED");
    }

    @Test
    void emptyExecutionCannotSucceed() {
        Run run = run();
        run.finish(NOW);
        assertStatus(run, "FAILED");
    }

    @Test
    void genericSkippedItemCannotMakeIncompleteIntegrationSuccessful() {
        Run run = run();
        add(run, "r1", "i1", RunItemResult.SKIPPED);
        run.finish(NOW);
        assertStatus(run, "FAILED");
    }

    @Test
    void laterSuccessCannotMaskAnEarlierFailedStep() {
        Run run = run();
        RunItem item = add(run, "r1", "i1", RunItemResult.MERGED);
        item.addStep(new RunStep(ActionType.ENSURE_MR, RunItemResult.FAILED, NOW, NOW, "MR rejected"));
        run.finish(NOW);
        assertStatus(run, "FAILED");
    }

    @Test
    void versionUpdateUsesTheSameExplicitRunStatus() {
        Run run = Run.start(RunType.VERSION_UPDATE, "tester", NOW);
        add(run, "r1", "i1", RunItemResult.VERSION_UPDATE_SUCCESS);
        run.finish(NOW);
        assertStatus(run, "DONE");
    }

    private Run run() {
        return Run.start(RunType.WINDOW_ORCHESTRATION, "tester", NOW);
    }

    private RunItem add(Run run, String repo, String iteration, RunItemResult result) {
        RunItem item = RunItem.create("W1", RepoId.of(repo), IterationKey.of(iteration), run.getItems().size() + 1, NOW);
        item.finishWith(result, NOW);
        run.addItem(item);
        return item;
    }

    private void assertStatus(Run run, String expected) {
        assertThat(run.getExecutionStatus()).isNotNull().extracting(Enum::name).isEqualTo(expected);
    }
}
