package io.releasehub.application.dataquality;

import java.util.List;

public record BranchCreationModeMigrationDryRunResult(
        String requestedBy,
        String sourceReport,
        int totalCandidates,
        int safeAutoDefaultableCount,
        int normalizeLegalValueCount,
        int manualMappingRequiredCount,
        int notMigratableCount,
        boolean executionPermitted,
        List<BranchCreationModeMigrationCandidateView> candidates,
        String markdownReport
) {
    public BranchCreationModeMigrationDryRunResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
