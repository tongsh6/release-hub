package io.releasehub.application.release;

import io.releasehub.domain.run.RunTask;

/**
 * 运行任务执行器接口
 */
public interface RunTaskExecutor {
    
    /**
     * 执行任务
     */
    void execute(RunTask task) throws Exception;
}
