package io.releasehub.application.release.executors;

import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 关闭迭代任务执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloseIterationExecutor extends AbstractRunTaskExecutor {
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.CLOSE_ITERATION;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String iterationKey = task.getTargetId();
        log.info("Closing iteration: {}", iterationKey);
        
        // TODO: 实现迭代关闭逻辑
        // 目前仅模拟
        log.info("Iteration {} closed successfully (mock)", iterationKey);
    }
}
