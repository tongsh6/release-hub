package io.releasehub.application.dataquality;

public record CleanupActionInput(
        String resourceType,
        String resourceId,
        String riskType,
        String suggestedAction,
        Boolean executed,
        String source,
        String applicationEntry,
        String preExecutionCheck,
        String postExecutionVerification,
        String reviewDecision
) {
}
