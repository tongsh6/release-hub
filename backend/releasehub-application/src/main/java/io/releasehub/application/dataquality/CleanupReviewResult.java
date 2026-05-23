package io.releasehub.application.dataquality;

import java.util.List;

public record CleanupReviewResult(
        String reviewer,
        String sourceReport,
        int total,
        int accepted,
        int pending,
        int rejected,
        List<CleanupActionReview> actions
) {
    public CleanupReviewResult {
        actions = List.copyOf(actions);
    }
}
