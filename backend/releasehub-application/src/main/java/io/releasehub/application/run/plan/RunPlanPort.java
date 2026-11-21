package io.releasehub.application.run.plan;

import java.util.List;
import java.util.Optional;

public interface RunPlanPort {
    /** 当前事务内串行化同一主体与幂等键的受理。 */
    void lockRequest(String operator, String idempotencyKey);
    Optional<SavedRunPlan> findByRequest(String operator, String idempotencyKey);
    Optional<SavedRunPlan> findByRunId(String runId);
    /** 持有窗口行锁时查找唯一收尾计划；不可选择最近一条掩盖重复。 */
    Optional<SavedRunPlan> findCleanupByWindowId(String windowId);
    /** 持有窗口行锁时检查已受理但未完成或待核查的执行；预览不计入。 */
    boolean hasUnresolvedExecutionByWindowId(String windowId);
    /** 持有同一窗口行锁时拒绝与活跃计划重叠的仓库；互不相交的仓库仍可受理。 */
    boolean hasUnresolvedExecutionForRepositories(String windowId, List<String> repoIds);
    void save(SavedRunPlan plan);
    List<RunPlanSummary> listByWindow(String windowId, int limit);
}
