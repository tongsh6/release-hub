package io.releasehub.application.dataquality;

public record CreateDispositionCaseCommand(
        String requestedBy,
        String sourceReport,
        CleanupActionReview action
) {
}
