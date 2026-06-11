package io.releasehub.application.dataquality;

import java.util.List;

public record CleanupReviewCommand(
        String reviewer,
        String sourceReport,
        List<CleanupActionInput> actions,
        String resourceTypeFilter,
        String riskTypeFilter,
        String reviewStatusFilter,
        String assetScopeFilter
) {
    public CleanupReviewCommand(String reviewer, String sourceReport, List<CleanupActionInput> actions) {
        this(reviewer, sourceReport, actions, null, null, null, null);
    }

    public CleanupReviewCommand(String reviewer, String sourceReport, List<CleanupActionInput> actions,
                                String resourceTypeFilter, String riskTypeFilter, String reviewStatusFilter) {
        this(reviewer, sourceReport, actions, resourceTypeFilter, riskTypeFilter, reviewStatusFilter, null);
    }

    public CleanupReviewCommand {
        actions = actions == null ? null : List.copyOf(actions);
    }
}
