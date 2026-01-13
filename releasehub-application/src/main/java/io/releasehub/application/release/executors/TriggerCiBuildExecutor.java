package io.releasehub.application.release.executors;

import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 触发 CI 构建任务执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerCiBuildExecutor extends AbstractRunTaskExecutor {
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.TRIGGER_CI_BUILD;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Triggering CI build for repo: {}", repoId);
        
        // TODO: 实现 CI 触发逻辑
        // 调用 GitLab CI API 触发构建
        
        log.info("CI build triggered for repo: {} (mock)", repoId);
    }
}
