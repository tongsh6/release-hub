package io.releasehub.application.port.out;

import io.releasehub.domain.run.MergeStatus;

/**
 * GitLab 分支操作端口
 */
public interface GitLabBranchPort {
    
    /**
     * 创建分支
     * @param repoCloneUrl 仓库克隆地址
     * @param branchName 新分支名
     * @param sourceBranch 源分支名
     * @return 是否创建成功
     */
    boolean createBranch(String repoCloneUrl, String branchName, String sourceBranch);
    
    /**
     * 检查分支是否存在
     * @param repoCloneUrl 仓库克隆地址
     * @param branchName 分支名
     * @return 是否存在
     */
    boolean branchExists(String repoCloneUrl, String branchName);
    
    /**
     * 合并分支
     * @param repoCloneUrl 仓库克隆地址
     * @param sourceBranch 源分支
     * @param targetBranch 目标分支
     * @param commitMessage 提交信息
     * @return 合并结果
     */
    MergeResult mergeBranch(String repoCloneUrl, String sourceBranch, String targetBranch, String commitMessage);
    
    /**
     * 归档分支（重命名为 archived/xxx）
     * @param repoCloneUrl 仓库克隆地址
     * @param branchName 分支名
     * @return 是否成功
     */
    boolean archiveBranch(String repoCloneUrl, String branchName, String reason);
    
    /**
     * 创建标签
     * @param repoCloneUrl 仓库克隆地址
     * @param tagName 标签名
     * @param ref 引用（分支名或commit sha）
     * @param message 标签信息
     * @return 是否成功
     */
    boolean createTag(String repoCloneUrl, String tagName, String ref, String message);
    
    /**
     * 合并结果
     */
    record MergeResult(MergeStatus status, String conflictInfo) {
        public static MergeResult success() {
            return new MergeResult(MergeStatus.SUCCESS, null);
        }
        
        public static MergeResult conflict(String info) {
            return new MergeResult(MergeStatus.CONFLICT, info);
        }
        
        public static MergeResult failed(String error) {
            return new MergeResult(MergeStatus.FAILED, error);
        }
    }
}
