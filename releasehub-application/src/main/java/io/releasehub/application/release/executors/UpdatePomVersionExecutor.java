package io.releasehub.application.release.executors;

import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 更新 POM 版本号任务执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdatePomVersionExecutor extends AbstractRunTaskExecutor {
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.UPDATE_POM_VERSION;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Updating POM version for repo: {}", repoId);
        
        // TODO: 实现版本号更新逻辑
        // 1. 读取当前 pom.xml 版本号
        // 2. 去除 -SNAPSHOT 后缀
        // 3. 提交变更
        
        log.info("POM version updated for repo: {} (mock)", repoId);
    }
}
