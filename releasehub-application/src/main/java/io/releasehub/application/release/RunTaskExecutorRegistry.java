package io.releasehub.application.release;

import io.releasehub.domain.run.RunTaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 任务执行器注册表
 */
@Slf4j
@Component
public class RunTaskExecutorRegistry {
    
    private final Map<RunTaskType, RunTaskExecutorPort> executors = new EnumMap<>(RunTaskType.class);
    
    public RunTaskExecutorRegistry(List<AbstractRunTaskExecutor> executorList) {
        for (AbstractRunTaskExecutor executor : executorList) {
            executors.put(executor.getTaskType(), executor);
            log.info("Registered executor for task type: {}", executor.getTaskType());
        }
    }
    
    public RunTaskExecutorPort getExecutor(RunTaskType taskType) {
        return executors.get(taskType);
    }
}
