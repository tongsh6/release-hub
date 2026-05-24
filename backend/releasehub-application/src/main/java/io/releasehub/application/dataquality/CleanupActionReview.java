package io.releasehub.application.dataquality;

public record CleanupActionReview(
        String resourceType,
        String resourceId,
        String riskType,
        String dataNamespace,
        String reviewBatchId,
        String assetScope,
        String retentionPolicy,
        String reviewStatus,
        String reason,
        String applicationEntry,
        String preExecutionCheck,
        String postExecutionVerification,
        String dispositionLevel,
        String allowedAction,
        String rollbackBoundary,
        String auditRecord,
        boolean executionPermitted
) {
    public CleanupActionReview(
            String resourceType,
            String resourceId,
            String riskType,
            String reviewStatus,
            String reason,
            String applicationEntry,
            String preExecutionCheck,
            String postExecutionVerification,
            boolean executionPermitted
    ) {
        this(resourceType, resourceId, riskType, null, null, null, null,
                reviewStatus, reason, applicationEntry, preExecutionCheck, postExecutionVerification,
                null, null, null, null, executionPermitted);
    }
}
