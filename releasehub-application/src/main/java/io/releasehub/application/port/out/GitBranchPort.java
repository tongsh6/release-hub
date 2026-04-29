package io.releasehub.application.port.out;

import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;

public interface GitBranchPort {

    boolean supports(GitProvider provider);

    boolean createBranch(String repoCloneUrl, String token, String branchName, String fromBranch);

    boolean deleteBranch(String repoCloneUrl, String token, String branchName);

    MergeResult mergeBranch(String repoCloneUrl, String token, String sourceBranch, String targetBranch, String commitMessage);

    boolean createTag(String repoCloneUrl, String token, String tagName, String ref, String message);

    BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName);

    record MergeResult(MergeStatus status, String detail) {
        public static MergeResult success() {
            return new MergeResult(MergeStatus.SUCCESS, null);
        }

        public static MergeResult conflict(String detail) {
            return new MergeResult(MergeStatus.CONFLICT, detail);
        }

        public static MergeResult failed(String detail) {
            return new MergeResult(MergeStatus.FAILED, detail);
        }
    }

    record BranchStatus(boolean exists, String latestCommit, int ahead, int behind) {
        public static BranchStatus missing() {
            return new BranchStatus(false, null, 0, 0);
        }

        public static BranchStatus present(String latestCommit) {
            return new BranchStatus(true, latestCommit, 0, 0);
        }
    }

    record MergeabilityResult(boolean canMerge, String detail) {
        public static MergeabilityResult mergeable() {
            return new MergeabilityResult(true, null);
        }

        public static MergeabilityResult conflict(String detail) {
            return new MergeabilityResult(false, detail);
        }

        public static MergeabilityResult error(String detail) {
            return new MergeabilityResult(false, detail);
        }
    }

    /**
     * 检查两个分支是否可合并（不实际执行合并）
     * @param repoCloneUrl 仓库克隆地址
     * @param token Git 访问令牌
     * @param sourceBranch 源分支
     * @param targetBranch 目标分支
     * @return MergeabilityResult, mergeable=true 表示无冲突可自动合并
     */
    MergeabilityResult checkMergeability(String repoCloneUrl, String token, String sourceBranch, String targetBranch);
}
