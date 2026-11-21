package io.releasehub.application.run;

import io.releasehub.application.port.out.GitBranchPort;

import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.Run;
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
    /** 按稳定 Run ID 游标发现当前协议的 PENDING 或无写入/结果证据的到期 RUNNING 候选；不授予执行资格。 */
    List<String> findTaskCandidateRunIds(String afterId, int limit);
    /** 在结果事务内仅补一个已确认的分支或 MR 创建结果；不访问 Git、不更换原 token 或期限。 */
    boolean projectNextAppliedTask(String runId);
    /** 锁内领取一个可执行任务；仅无 operation/Step 的完整到期领取可更换 token，既有写入不重派。 */
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

    record TaskFence(String taskId, String claimToken) {}
    /** startedAt 是当前持有者的执行起点；TaskView.startedAt 保留任务首次领取时间。 */
    record TaskClaim(TaskFence fence, String runId, String itemId, RepoId repo, ActionType actionType,
                     Instant startedAt, String operator, Map<String, String> metadata) {
        public TaskClaim { metadata = Map.copyOf(metadata); }
        @Override public Map<String, String> metadata() { return Map.copyOf(metadata); }
    }
    record TaskView(String taskId, String runId, String itemId, ActionType actionType, int taskOrder,
                    String status, Instant startedAt, Instant finishedAt, String errorMessage) {}
}
