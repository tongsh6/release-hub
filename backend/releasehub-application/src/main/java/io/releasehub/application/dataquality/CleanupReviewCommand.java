package io.releasehub.application.dataquality;

import java.util.List;

public record CleanupReviewCommand(
        String reviewer,
        String sourceReport,
        List<CleanupActionInput> actions
) {
    public CleanupReviewCommand {
        actions = actions == null ? null : List.copyOf(actions);
    }
}
