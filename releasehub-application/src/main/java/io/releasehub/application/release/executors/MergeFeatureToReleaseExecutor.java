package io.releasehub.application.release.executors;

import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 合并 feature 到 release 分支任务执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MergeFeatureToReleaseExecutor extends AbstractRunTaskExecutor {
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.MERGE_FEATURE_TO_RELEASE;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Merging feature to release for repo: {}", repoId);
        
        // 此任务通常通过 CodeMergeService 手动或自动触发
        // 这里作为备用
        
        log.info("Feature merged to release for repo: {} (mock)", repoId);
    }
}
