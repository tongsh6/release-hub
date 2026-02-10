package io.releasehub.application.release.executors;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.application.run.RunTaskContext;
import io.releasehub.application.run.RunTaskContextPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * 关闭迭代任务执行器
 * 将迭代状态从 ACTIVE 更新为 CLOSED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloseIterationExecutor extends AbstractRunTaskExecutor {
    
    private final IterationPort iterationPort;
    private final RunTaskContextPort runTaskContextPort;
    private final Clock clock = Clock.systemUTC();
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.CLOSE_ITERATION;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String iterationKeyValue = task.getTargetId();
        log.info("Closing iteration: {}", iterationKeyValue);
        
        // 从上下文获取额外信息（如有需要）
        RunTaskContext context = runTaskContextPort.getContext(task).orElse(null);
        if (context != null) {
            log.debug("Iteration context - windowKey: {}, repoId: {}", 
                    context.getWindowKey(), context.getRepoId());
        }
        
        // 获取迭代
        IterationKey iterationKey = IterationKey.of(iterationKeyValue);
        Iteration iteration = iterationPort.findByKey(iterationKey)
                .orElseThrow(() -> BusinessException.of(ErrorCode.ITERATION_NOT_FOUND, iterationKeyValue));
        
        // 检查是否已关闭（幂等）
        if (iteration.isClosed()) {
            log.info("Iteration {} is already closed, skipping", iterationKeyValue);
            return;
        }
        
        // 关闭迭代
        iteration.close(Instant.now(clock));
        iterationPort.save(iteration);
        
        log.info("Iteration {} closed successfully", iterationKeyValue);
    }
}
