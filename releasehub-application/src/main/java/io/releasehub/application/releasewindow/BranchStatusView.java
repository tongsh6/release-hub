package io.releasehub.application.releasewindow;

import java.util.List;

/**
 * 分支状态视图：展示发布窗口内各仓库的分支状态
 */
public record BranchStatusView(
        String windowId,
        String windowKey,
        List<RepoBranchStatus> repos
) {

    /**
     * 单个仓库的分支状态
     */
    public record RepoBranchStatus(
            String repoId,
            String repoName,
            String repoCloneUrl,
            String iterationKey,
            FeatureBranchInfo featureBranch,
            ReleaseBranchInfo releaseBranch
    ) {}

    /**
     * Feature 分支状态
     */
    public record FeatureBranchInfo(
            String branchName,
            boolean exists,
            String latestCommit
    ) {}

    /**
     * Release 分支状态
     */
    public record ReleaseBranchInfo(
            String branchName,
            boolean exists,
            String latestCommit,
            String mergeStatus
    ) {
        public static ReleaseBranchInfo missing(String branchName) {
            return new ReleaseBranchInfo(branchName, false, null, "PENDING");
        }
    }
}
