package io.releasehub.application.dataquality;

public record BranchCreationModeMigrationCandidateView(
        String candidateKey,
        String iterationKey,
        String repoId,
        String currentModeRaw,
        String featureBranch,
        String proposedMode,
        String classification,
        String inferenceReason,
        boolean requiresManualMapping,
        boolean executableAfterApproval,
        String executionPlanDraft,
        String rejectionReason
) {
}
