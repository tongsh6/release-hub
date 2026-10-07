package io.releasehub.domain.run;

import java.util.List;

/** 不含凭据的不可变本地预览快照，不能直接派发外部操作。 */
public record RunPlanSnapshot(String windowKey, String groupCode, String windowStatus, boolean frozen,
                              boolean failFast, List<Item> items) {
    public RunPlanSnapshot {
        items = List.copyOf(items);
    }

    public record Item(String itemId, int plannedOrder, String repoId, String repoName, String iterationKey,
                       String gitProvider, String defaultBranch, String featureBranch, String releaseBranch,
                       String baseVersion, String devVersion, String targetVersion, List<String> steps) {
        public Item {
            steps = List.copyOf(steps);
        }
    }
}
