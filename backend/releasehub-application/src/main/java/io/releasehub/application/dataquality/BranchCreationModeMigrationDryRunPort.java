package io.releasehub.application.dataquality;

import java.util.List;

public interface BranchCreationModeMigrationDryRunPort {
    List<IterationRepoBranchModeRecord> listIterationRepoBranchModes();

    record IterationRepoBranchModeRecord(
            String iterationKey,
            String repoId,
            String branchCreationMode,
            String featureBranch
    ) {
    }
}
