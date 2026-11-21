package io.releasehub.application.run;

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
    /** 一次性领取；没有租约超时重派或隐式恢复。 */
    Optional<TaskClaim> claimNextTask(String runId);
    /** 短事务内定向保存事实、投影、绑定和 guard 确认；不保存传入的整个 Run。 */
    Run completeTask(TaskClaim claim, RunStep step, Map<String, String> metadataUpdates);
    List<TaskView> findTasks(String runId);

    record TaskFence(String taskId, String claimToken) {}
    record TaskClaim(TaskFence fence, String runId, String itemId, RepoId repo, ActionType actionType,
                     Instant startedAt, String operator, Map<String, String> metadata) {
        public TaskClaim { metadata = Map.copyOf(metadata); }
        @Override public Map<String, String> metadata() { return Map.copyOf(metadata); }
    }
    record TaskView(String taskId, String runId, String itemId, ActionType actionType, int taskOrder,
                    String status, Instant startedAt, Instant finishedAt, String errorMessage) {}
}
