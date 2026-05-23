package io.releasehub.application.dataquality;

public record CleanupActionReview(
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
}
