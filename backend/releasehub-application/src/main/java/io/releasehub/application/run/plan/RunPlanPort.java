package io.releasehub.application.run.plan;

import java.util.List;
import java.util.Optional;

public interface RunPlanPort {
    /** 当前事务内串行化同一主体与幂等键的受理。 */
    void lockRequest(String operator, String idempotencyKey);
    Optional<SavedRunPlan> findByRequest(String operator, String idempotencyKey);
    Optional<SavedRunPlan> findByRunId(String runId);
    void save(SavedRunPlan plan);
    List<RunPlanSummary> listByWindow(String windowId, int limit);
}
