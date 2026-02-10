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
 * 创建标签任务执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateTagExecutor extends AbstractRunTaskExecutor {
    
    private final GitLabBranchPort gitLabBranchPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final RunTaskContextPort runTaskContextPort;
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.CREATE_TAG;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Creating tag for repo: {}", repoId);
        
        CodeRepository repo = codeRepositoryPort.findById(RepoId.of(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));
        
        // 从上下文获取版本号作为标签名
        RunTaskContext context = runTaskContextPort.getContext(task)
                .orElseThrow(() -> BusinessException.runTaskContextNotFound(task.getId().value()));
        
        String targetVersion = context.getTargetVersion();
        if (targetVersion == null || targetVersion.isBlank()) {
            throw BusinessException.runTaskContextNotFound("Target version not found for task " + task.getId().value());
        }
        
        String tagName = "v" + targetVersion;
        String masterBranch = repo.getDefaultBranch();
        
        boolean success = gitLabBranchPort.createTag(
                repo.getCloneUrl(), tagName, masterBranch,
                "Release " + tagName);
        
        if (!success) {
            throw BusinessException.runTaskTagCreateFailed(tagName);
        }
        
        log.info("Tag {} created for repo: {}", tagName, repoId);
    }
}
