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
 * 归档 feature 分支任务执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveFeatureBranchExecutor extends AbstractRunTaskExecutor {
    
    private final GitLabBranchPort gitLabBranchPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final RunTaskContextPort runTaskContextPort;
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.ARCHIVE_FEATURE_BRANCH;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Archiving feature branch for repo: {}", repoId);
        
        CodeRepository repo = codeRepositoryPort.findById(RepoId.of(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));
        
        // 从上下文获取 feature 分支名
        RunTaskContext context = runTaskContextPort.getContext(task)
                .orElseThrow(() -> BusinessException.runTaskContextNotFound(task.getId().value()));
        
        String featureBranch = context.getFeatureBranch();
        if (featureBranch == null || featureBranch.isBlank()) {
            log.warn("Feature branch not found in context for repo: {}, skipping archive", repoId);
            return;
        }
        
        boolean success = gitLabBranchPort.archiveBranch(repo.getCloneUrl(), featureBranch, "released");
        if (!success) {
            log.warn("Failed to archive branch {} (may not exist)", featureBranch);
        }
        
        log.info("Feature branch {} archived for repo: {}", featureBranch, repoId);
    }
}
