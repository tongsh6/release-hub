package io.releasehub.application.release.executors;

import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.application.repo.CodeRepositoryPort;
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
        
        // TODO: 获取版本号作为标签名
        String tagName = "v1.0.0"; // 需要从上下文获取
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
