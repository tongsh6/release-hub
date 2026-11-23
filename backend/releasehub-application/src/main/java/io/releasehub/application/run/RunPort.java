package io.releasehub.application.run;

import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.port.out.GitBranchPort;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunStep;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RunPort {
    /** 仅用于新记录及没有持久任务的既有执行类型；不得级联覆盖任务协议 Run。 */
    void save(Run run);
    Optional<Run> findById(String runId);
    List<Run> findAll();
    List<Run> findByWindowKey(String windowKey);
    PageResult<Run> findPaged(String runType, String operator, String windowKey, String repoId, String iterationKey, String status, String groupCode, int page, int size);

    /** 加入受理事务，按冻结 Item 范围建立固定四动作；预览不调用。 */
    void createTasks(Run run);
    /** 有界发现当前协议的 PENDING、无证据到期领取及末尾 MERGE 的 INTENT/APPLIED/REJECTED 候选；锁内另核完整资格。 */
    List<String> findTaskCandidateRunIds(String afterId, int limit);
    /** 仅凭原同步 APPLIED/REJECTED 证明补一个任务结果；不消费 Observation、不访问 Git、不换 token 或期限。 */
    boolean projectNextConfirmedTask(String runId);
    /** 锁内领取一个任务；无事实或具备完整未发送分支/MERGE 证明的到期领取可换 token，SEND 及未知结果不重派。 */
    Optional<TaskClaim> claimNextTask(String runId);
    /** 短事务内定向保存事实、投影、绑定和 guard 确认；不保存传入的整个 Run。 */
    Run completeTask(TaskClaim claim, RunStep step, Map<String, String> metadataUpdates);
    List<TaskView> findTasks(String runId);

    /** 正常 ENSURE_MR 与已确认结果投影共用；null 值用于删除过期事实，requestId 仅表示真实 HTTP 请求 ID。 */
    static Map<String, String> mrEvidenceUpdates(GitBranchPort.MrEvidence evidence, String requestId) {
        Map<String, String> metadata = new java.util.LinkedHashMap<>();
        metadata.put("git.requestId", requestId);
        metadata.put("git.blockReason", evidence == null || evidence.blockReason() == null ? null : evidence.blockReason().name());
        if (evidence != null) {
            if (evidence.projectId() != null) metadata.put("git.projectId", evidence.projectId());
            metadata.put("git.sourceBranch", evidence.sourceBranch());
            metadata.put("git.targetBranch", evidence.targetBranch());
            metadata.put("git.mrIid", evidence.iid());
            metadata.put("git.mrUrl", evidence.url());
            metadata.put("git.sourceSha", evidence.sourceSha());
            metadata.put("git.mergeSha", evidence.mergeSha());
            metadata.put("git.mrDisposition", evidence.disposition() == null ? null : evidence.disposition().name());
        }
        return metadata;
    }

    /** 读取原 ENSURE_MR 身份；具体消费者仍须验证形状、计划及动作资格。 */
    static GitBranchPort.MrEvidence mrCheckpoint(Map<String, String> metadata) {
        return new GitBranchPort.MrEvidence(metadata.get("git.projectId"), metadata.get("git.sourceBranch"),
                metadata.get("git.targetBranch"), metadata.get("git.mrIid"), metadata.get("git.mrUrl"),
                metadata.get("git.sourceSha"), metadata.get("git.mergeSha"),
                GitBranchPort.MrDisposition.valueOf(metadata.getOrDefault("git.mrDisposition", "")), null);
    }

    /** 正常合并与原同步响应投影共用的业务结果映射。 */
    static RunItemResult mergeOutcome(GitBranchPort.MergeResult result) {
        return switch (result.status()) {
            case SUCCESS -> result.mrEvidence().disposition() == GitBranchPort.MrDisposition.ALREADY_MERGED
                    ? RunItemResult.ALREADY_MERGED : RunItemResult.MERGED;
            case CONFLICT, BLOCKED -> RunItemResult.MERGE_BLOCKED;
            case UNKNOWN -> RunItemResult.UNKNOWN;
            case FAILED -> RunItemResult.FAILED;
        };
    }

    /** 正常 TRY 与历史合并结果投影共用，只更新结果；null 值删除旧字段，不改检查点身份。 */
    static Map<String, String> mrResultUpdates(GitBranchPort.MergeResult result) {
        Map<String, String> updates = new java.util.LinkedHashMap<>();
        updates.put("git.requestId", result.remoteRequestId());
        var evidence = result.mrEvidence();
        updates.put("git.blockReason", evidence == null || evidence.blockReason() == null
                ? (result.status() == io.releasehub.domain.run.MergeStatus.UNKNOWN ? "UNKNOWN" : null)
                : evidence.blockReason().name());
        if (result.status() == io.releasehub.domain.run.MergeStatus.SUCCESS) {
            updates.put("git.mergeSha", evidence.mergeSha());
            updates.put("git.mrDisposition", evidence.disposition().name());
        }
        return updates;
    }

    record TaskFence(String taskId, String claimToken) {}
    /** startedAt 是当前持有者的执行起点；TaskView.startedAt 保留任务首次领取时间。 */
    record TaskClaim(TaskFence fence, String runId, String itemId, RepoId repo, ActionType actionType,
                     Instant startedAt, String operator, Map<String, String> metadata,
                     GitOperationJournalPort.Operation unsentOperation) {
        public TaskClaim { metadata = Map.copyOf(metadata); }
        @Override public Map<String, String> metadata() { return Map.copyOf(metadata); }
    }
    record TaskView(String taskId, String runId, String itemId, ActionType actionType, int taskOrder,
                    String status, Instant startedAt, Instant finishedAt, String errorMessage) {}
}
