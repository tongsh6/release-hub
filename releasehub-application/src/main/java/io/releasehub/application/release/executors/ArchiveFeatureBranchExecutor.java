package io.releasehub.application.release.executors;

import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.application.repo.CodeRepositoryPort;
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
        
        // TODO: 获取 feature 分支名
        String featureBranch = "feature/ITER-xxx"; // 需要从上下文获取
        
        boolean success = gitLabBranchPort.archiveBranch(repo.getCloneUrl(), featureBranch, "released");
        if (!success) {
            log.warn("Failed to archive branch {} (may not exist)", featureBranch);
        }
        
        log.info("Feature branch archived for repo: {} (mock)", repoId);
    }
}
