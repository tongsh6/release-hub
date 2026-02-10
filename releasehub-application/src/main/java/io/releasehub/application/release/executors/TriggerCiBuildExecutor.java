package io.releasehub.application.release.executors;

import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunTaskContext;
import io.releasehub.application.run.RunTaskContextPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
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
    
    private final GitLabBranchPort gitLabBranchPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final RunTaskContextPort runTaskContextPort;
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.TRIGGER_CI_BUILD;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Triggering CI build for repo: {}", repoId);
        
        CodeRepository repo = codeRepositoryPort.findById(RepoId.of(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));
        
        // 从上下文获取要触发构建的分支
        RunTaskContext context = runTaskContextPort.getContext(task)
                .orElseThrow(() -> BusinessException.runTaskContextNotFound(task.getId().value()));
        
        // 优先使用 release 分支，其次是 master 分支
        String ref = context.getReleaseBranch();
        if (ref == null || ref.isBlank()) {
            ref = repo.getDefaultBranch();
        }
        
        String pipelineId = gitLabBranchPort.triggerPipeline(repo.getCloneUrl(), ref);
        
        if (pipelineId == null) {
            throw BusinessException.runTaskCiTriggerFailed("Failed to trigger pipeline for " + ref);
        }
        
        log.info("CI build triggered for repo: {}, ref: {}, pipelineId: {}", repoId, ref, pipelineId);
    }
}
