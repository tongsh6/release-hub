package io.releasehub.application.port.out;

import io.releasehub.application.gitoperation.GitReadResult;
import io.releasehub.application.gitoperation.GitWriteResult;

import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.run.MergeStatus;

import java.util.List;

public interface GitBranchPort {

    boolean supports(GitProvider provider);

    boolean createBranch(String repoCloneUrl, String token, String branchName, String fromBranch);

    boolean deleteBranch(String repoCloneUrl, String token, String branchName);

    MergeResult mergeBranch(String repoCloneUrl, String token, String sourceBranch, String targetBranch, String commitMessage);

    /**
     * Confirms the ENSURE_MR goal with at most one create request.
     * APPLIED may mean read-only reuse or verified containment; only TRY_MERGE may complete a merge task.
     */
    GitWriteResult<MrEvidence> ensureMergeRequest(String repoCloneUrl, String token, String sourceBranch,
                                                  String targetBranch, String message);

    /** Uses the committed MR checkpoint and its expected numeric project identity; never creates an MR. */
    MergeResult tryMergeRequest(String repoCloneUrl, String token, MrEvidence checkpoint, String message);

    /** Only a confirmed numeric project identity and full commit SHA may authorize a release tag. */
    GitReadResult<TagEvidence> readTag(String repoCloneUrl, String token, String expectedProjectId, String tagName);

    GitWriteResult<TagEvidence> createTag(String repoCloneUrl, String token, String expectedProjectId,
                                         String tagName, String targetSha, String message);

    record TagEvidence(String projectId, String tagName, String commitSha) {}

    record PipelineEvidence(String projectId, String id, String ref, String sha, String url) {}

    BranchStatus getBranchStatus(String repoCloneUrl, String token, String branchName);

    enum MrDisposition { CREATED, MR_REUSED, ALREADY_MERGED }

    enum MergeBlockReason {
        CONFLICT, PIPELINE_PENDING, PIPELINE_FAILED, APPROVAL_REQUIRED,
        PROTECTED_BRANCH, PERMISSION_DENIED, UNKNOWN
    }

    record MrEvidence(String projectId, String sourceBranch, String targetBranch,
                      String iid, String url, String sourceSha, String mergeSha,
                      MrDisposition disposition, MergeBlockReason blockReason) {
        public MrEvidence withBlockReason(MergeBlockReason reason) {
            return new MrEvidence(projectId, sourceBranch, targetBranch, iid, url, sourceSha, mergeSha,
                    disposition, reason);
        }
    }

    /** remoteRequestId 仅表示远端 HTTP 请求 ID；未获得该证据时保持 null，MR 编号独立存于 mrEvidence.iid。 */
    record MergeResult(MergeStatus status, String detail, String remoteRequestId, MrEvidence mrEvidence) {
        public static MergeResult success() {
            return new MergeResult(MergeStatus.SUCCESS, null, null, null);
        }

        public static MergeResult conflict(String detail) {
            return blocked(MergeBlockReason.CONFLICT, detail);
        }

        public static MergeResult blocked(MergeBlockReason reason, String detail) {
            return new MergeResult(reason == MergeBlockReason.CONFLICT ? MergeStatus.CONFLICT : MergeStatus.BLOCKED,
                    detail, null, new MrEvidence(null, null, null, null, null, null, null, null, reason));
        }

        public static MergeResult failed(String detail) {
            return new MergeResult(MergeStatus.FAILED, detail, null, null);
        }

        public static MergeResult unknown(String detail) {
            return new MergeResult(MergeStatus.UNKNOWN, detail, null,
                    new MrEvidence(null, null, null, null, null, null, null, null, MergeBlockReason.UNKNOWN));
        }

        public MergeResult withEvidence(MrEvidence evidence) {
            if (evidence == null) return this;
            if (mrEvidence != null && mrEvidence.blockReason() != null) {
                evidence = evidence.withBlockReason(mrEvidence.blockReason());
            }
            return new MergeResult(status, detail, remoteRequestId, evidence);
        }
    }

    /** GitLab reads carry the confirmed numeric project identity even for a genuine branch 404. */
    record BranchStatus(boolean exists, String latestCommit, int ahead, int behind, String projectId) {
        public static BranchStatus missing(String projectId) {
            return new BranchStatus(false, null, 0, 0, projectId);
        }

        public static BranchStatus present(String projectId, String latestCommit) {
            return new BranchStatus(true, latestCommit, 0, 0, projectId);
        }
    }

    enum MergeabilityFailure {
        NONE,
        CONFLICT,
        PERMISSION_DENIED,
        UNAVAILABLE,
        UNKNOWN
    }

    record MergeabilityResult(boolean canMerge, String detail, MergeabilityFailure failure) {
        public static MergeabilityResult mergeable() {
            return new MergeabilityResult(true, null, MergeabilityFailure.NONE);
        }

        public static MergeabilityResult conflict(String detail) {
            return new MergeabilityResult(false, detail, MergeabilityFailure.CONFLICT);
        }

        public static MergeabilityResult permissionDenied(String detail) {
            return new MergeabilityResult(false, detail, MergeabilityFailure.PERMISSION_DENIED);
        }

        public static MergeabilityResult unavailable(String detail) {
            return new MergeabilityResult(false, detail, MergeabilityFailure.UNAVAILABLE);
        }

        public static MergeabilityResult error(String detail) {
            return new MergeabilityResult(false, detail, MergeabilityFailure.UNKNOWN);
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
     * @param expectedProjectId 本次已确认的远端项目 ID
     * @param expectedSha 本次发布的确切提交；结果不符时保持 UNKNOWN，不可自动重放
     * @return 确认创建、明确拒绝或结果未决，以及完整流水线证据
     */
    GitWriteResult<PipelineEvidence> triggerPipeline(String repoCloneUrl, String token, String expectedProjectId,
                                                     String ref, String expectedSha);

    /**
     * 列出仓库中匹配前缀的分支名。
     * @param repoCloneUrl 仓库克隆地址
     * @param token Git 访问令牌
     * @param prefix 分支名前缀（如 "feature/"）；空字符串表示列出全部分支
     * @return 匹配的分支名列表
     */
    List<String> listBranches(String repoCloneUrl, String token, String prefix);
}
