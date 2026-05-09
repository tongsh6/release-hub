package io.releasehub.application.port.out;

import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;

import java.util.List;

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

    /**
     * 归档分支（重命名并标记为归档）
     * @param repoCloneUrl 仓库克隆地址
     * @param token Git 访问令牌
     * @param branchName 要归档的分支名
     * @param reason 归档原因
     * @return true 表示归档成功
     */
    boolean archiveBranch(String repoCloneUrl, String token, String branchName, String reason);

    /**
     * 触发 CI 流水线
     * @param repoCloneUrl 仓库克隆地址
     * @param token Git 访问令牌
     * @param ref 分支名或标签名
     * @return 流水线 ID，失败返回 null
     */
    String triggerPipeline(String repoCloneUrl, String token, String ref);

    /**
     * 列出仓库中匹配前缀的分支名。
     * @param repoCloneUrl 仓库克隆地址
     * @param token Git 访问令牌
     * @param prefix 分支名前缀（如 "feature/"）
     * @return 匹配的分支名列表
     */
    List<String> listBranches(String repoCloneUrl, String token, String prefix);
}
