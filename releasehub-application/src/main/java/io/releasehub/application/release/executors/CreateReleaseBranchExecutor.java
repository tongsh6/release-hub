package io.releasehub.application.release.executors;

import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 创建 release 分支任务执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateReleaseBranchExecutor extends AbstractRunTaskExecutor {
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.CREATE_RELEASE_BRANCH;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Creating release branch for repo: {}", repoId);
        
        // 此任务通常在迭代关联窗口时已经执行
        // 这里作为备用
        
        log.info("Release branch created for repo: {} (mock)", repoId);
    }
}
