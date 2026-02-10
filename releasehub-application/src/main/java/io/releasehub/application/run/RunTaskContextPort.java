package io.releasehub.application.run;

import io.releasehub.domain.run.RunTask;

import java.util.Optional;

/**
 * 运行任务上下文端口
 * 提供从 RunTask 获取执行所需上下文信息的能力
 */
public interface RunTaskContextPort {
    
    /**
     * 获取任务执行上下文
     * 
     * @param task 运行任务
     * @return 任务上下文，包含 windowKey、iterationKey、分支名、版本号等信息
     */
    Optional<RunTaskContext> getContext(RunTask task);
}
