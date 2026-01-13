package io.releasehub.application.run;

import io.releasehub.domain.run.RunTask;

import java.util.List;
import java.util.Optional;

/**
 * RunTask 持久化端口
 */
public interface RunTaskPort {
    
    void save(RunTask task);
    
    Optional<RunTask> findById(String id);
    
    List<RunTask> findByRunId(String runId);
    
    List<RunTask> findByRunIdAndStatus(String runId, String status);
}
