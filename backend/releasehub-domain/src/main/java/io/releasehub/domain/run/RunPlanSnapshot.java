package io.releasehub.domain.run;

import java.util.List;

/** 不含凭据的不可变编排计划快照；Run 状态区分预览和已受理执行。 */
public record RunPlanSnapshot(String windowKey, String groupCode, String windowStatus, boolean frozen,
                              boolean failFast, List<Item> items) {
    public RunPlanSnapshot {
        items = List.copyOf(items);
    }

    public record Item(String itemId, int plannedOrder, String repoId, String repoName, String iterationKey,
                       String gitProvider, String defaultBranch, String featureBranch, String featureBranchMode, String releaseBranch,
                       String baseVersion, String devVersion, String targetVersion, List<String> steps,
                       List<String> participatingIterationKeys, String tagName, boolean triggerCi, String ciRef) {
        public Item {
            steps = List.copyOf(steps);
            participatingIterationKeys = participatingIterationKeys == null ? List.of() : List.copyOf(participatingIterationKeys);
        }
    }
}
