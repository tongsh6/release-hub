package io.releasehub.application.dataquality;

import io.releasehub.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BranchCreationModeMigrationDryRunAppServiceTest {
    @Test
    void shouldClassifyBranchCreationModeCandidatesWithoutPermittingExecution() {
        BranchCreationModeMigrationDryRunAppService service = new BranchCreationModeMigrationDryRunAppService(() -> List.of(
                row("ITER-1", "repo-1", null, "feature/ITER-1"),
                row("ITER-2", "repo-2", " named ", "feature/custom"),
                row("ITER-3", "repo-3", null, "feature/custom"),
                row("ITER-4", "repo-4", "legacy", ""),
                row("ITER-5", "repo-5", "AUTO", "feature/ITER-5")
        ));

        BranchCreationModeMigrationDryRunResult result = service.dryRun(new BranchCreationModeMigrationDryRunCommand(
                "release-manager",
                ".ai/reports/sa002-safe-cleanup/actions.jsonl"));

        assertThat(result.executionPermitted()).isFalse();
        assertThat(result.totalCandidates()).isEqualTo(4);
        assertThat(result.safeAutoDefaultableCount()).isEqualTo(1);
        assertThat(result.normalizeLegalValueCount()).isEqualTo(1);
        assertThat(result.manualMappingRequiredCount()).isEqualTo(1);
        assertThat(result.notMigratableCount()).isEqualTo(1);
        assertThat(result.candidates())
                .extracting(BranchCreationModeMigrationCandidateView::classification)
                .containsExactly(
                        "SAFE_AUTO_DEFAULTABLE",
                        "NORMALIZE_LEGAL_VALUE",
                        "MANUAL_MAPPING_REQUIRED",
                        "NOT_MIGRATABLE_IN_THIS_SERVICE");
        assertThat(result.candidates().get(0).proposedMode()).isEqualTo("AUTO");
        assertThat(result.candidates().get(1).proposedMode()).isEqualTo("NAMED");
        assertThat(result.candidates().get(2).requiresManualMapping()).isTrue();
        assertThat(result.candidates().get(3).executableAfterApproval()).isFalse();
        assertThat(result.candidates().get(0).executionPlanDraft()).contains("set branchCreationMode to AUTO");
        assertThat(result.candidates().get(2).executionPlanDraft()).contains("Manual mapping is required");
        assertThat(result.markdownReport()).contains("executionPermitted: false");
        assertThat(result.markdownReport()).contains("executionPlanDraft");
        assertThat(result.markdownReport()).contains("SAFE_AUTO_DEFAULTABLE");
        assertThat(result.markdownReport()).doesNotContain("ITER-5");
    }

    @Test
    void shouldRequireRequestedBy() {
        BranchCreationModeMigrationDryRunAppService service = new BranchCreationModeMigrationDryRunAppService(List::of);

        assertThatThrownBy(() -> service.dryRun(new BranchCreationModeMigrationDryRunCommand("", "report")))
                .isInstanceOf(ValidationException.class);
    }

    private BranchCreationModeMigrationDryRunPort.IterationRepoBranchModeRecord row(String iterationKey, String repoId,
                                                                                    String mode, String branch) {
        return new BranchCreationModeMigrationDryRunPort.IterationRepoBranchModeRecord(iterationKey, repoId, mode, branch);
    }
}
