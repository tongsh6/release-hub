package io.releasehub.application.releasewindow;

import io.releasehub.application.window.PlanItemView;

import java.time.Instant;
import java.util.List;

public record ReleaseWindowParallelScopeView(
        String currentWindowId,
        String currentWindowKey,
        String groupCode,
        int activeWindowCount,
        List<ParallelWindowView> windows
) {
    public ReleaseWindowParallelScopeView {
        windows = windows == null ? List.of() : List.copyOf(windows);
    }

    @Override
    public List<ParallelWindowView> windows() {
        return List.copyOf(windows);
    }

    public record ParallelWindowView(
            String windowId,
            String windowKey,
            String name,
            String status,
            Instant plannedReleaseAt,
            int iterationCount,
            int repoCount,
            List<PlanItemView> planItems
    ) {
        public ParallelWindowView {
            planItems = planItems == null ? List.of() : List.copyOf(planItems);
        }

        @Override
        public List<PlanItemView> planItems() {
            return List.copyOf(planItems);
        }
    }
}
