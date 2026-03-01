package io.releasehub.application.release.executors;

import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
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
 * 创建 release 分支任务执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateReleaseBranchExecutor extends AbstractRunTaskExecutor {

    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final CodeRepositoryPort codeRepositoryPort;
    private final RunTaskContextPort runTaskContextPort;
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.CREATE_RELEASE_BRANCH;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Creating release branch for repo: {}", repoId);

        CodeRepository repo = codeRepositoryPort.findById(RepoId.of(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));

        RunTaskContext context = runTaskContextPort.getContext(task)
                .orElseThrow(() -> BusinessException.runTaskContextNotFound(task.getId().value()));

        String releaseBranch = context.getReleaseBranch();
        if (releaseBranch == null || releaseBranch.isBlank()) {
            if (context.getWindowKey() == null || context.getWindowKey().isBlank()) {
                throw BusinessException.runTaskContextNotFound("Release branch not found for task " + task.getId().value());
            }
            releaseBranch = "release/" + context.getWindowKey();
        }

        GitBranchPort gitBranchPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
        if (gitBranchPort.getBranchStatus(repo.getCloneUrl(), repo.getGitToken(), releaseBranch).exists()) {
            log.info("Release branch already exists: {}", releaseBranch);
            return;
        }

        boolean created = gitBranchPort.createBranch(
                repo.getCloneUrl(),
                repo.getGitToken(),
                releaseBranch,
                repo.getDefaultBranch()
        );

        if (!created) {
            throw BusinessException.runTaskMergeFailed("Failed to create release branch: " + releaseBranch);
        }

        log.info("Release branch created for repo: {}, branch: {}", repoId, releaseBranch);
    }
}
