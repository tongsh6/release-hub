package io.releasehub.application.release;

import io.releasehub.domain.run.RunTaskType;

/**
 * 抽象任务执行器基类
 */
public abstract class AbstractRunTaskExecutor implements RunTaskExecutor {
    
    /**
     * 获取任务类型
     */
    public abstract RunTaskType getTaskType();
}
