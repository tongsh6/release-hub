package io.releasehub.application.dataquality;

public record CleanupActionInput(
        String resourceType,
        String resourceId,
        String riskType,
        String suggestedAction,
        Boolean executed,
        String source,
        String dataNamespace,
        String reviewBatchId,
        String assetScope,
        String retentionPolicy,
        String applicationEntry,
        String preExecutionCheck,
        String postExecutionVerification,
        String reviewDecision
) {
    public CleanupActionInput(
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
        this(resourceType, resourceId, riskType, suggestedAction, executed, source,
                null, null, null, null,
                applicationEntry, preExecutionCheck, postExecutionVerification, reviewDecision);
    }
}
