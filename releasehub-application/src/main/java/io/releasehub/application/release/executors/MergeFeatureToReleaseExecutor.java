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
import io.releasehub.domain.run.MergeStatus;
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

    private final GitBranchAdapterFactory gitBranchAdapterFactory;
    private final CodeRepositoryPort codeRepositoryPort;
    private final RunTaskContextPort runTaskContextPort;
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.MERGE_FEATURE_TO_RELEASE;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Merging feature to release for repo: {}", repoId);

        CodeRepository repo = codeRepositoryPort.findById(RepoId.of(repoId))
                .orElseThrow(() -> NotFoundException.repository(repoId));

        RunTaskContext context = runTaskContextPort.getContext(task)
                .orElseThrow(() -> BusinessException.runTaskContextNotFound(task.getId().value()));

        String featureBranch = context.getFeatureBranch();
        String releaseBranch = context.getReleaseBranch();
        if (featureBranch == null || featureBranch.isBlank() || releaseBranch == null || releaseBranch.isBlank()) {
            throw BusinessException.runTaskContextNotFound("Feature/release branch not found for task " + task.getId().value());
        }

        GitBranchPort gitBranchPort = gitBranchAdapterFactory.getAdapter(repo.getGitProvider());
        String repoUrl = repo.getCloneUrl();
        String gitToken = repo.getGitToken();

        if (!gitBranchPort.getBranchStatus(repoUrl, gitToken, featureBranch).exists()) {
            log.info("Feature branch {} does not exist, skip merge", featureBranch);
            return;
        }

        if (!gitBranchPort.getBranchStatus(repoUrl, gitToken, releaseBranch).exists()) {
            throw BusinessException.runTaskMergeFailed("Release branch does not exist: " + releaseBranch);
        }

        GitBranchPort.MergeResult result = gitBranchPort.mergeBranch(
                repoUrl,
                gitToken,
                featureBranch,
                releaseBranch,
                "Merge " + featureBranch + " into " + releaseBranch
        );

        if (result.status() == MergeStatus.CONFLICT) {
            throw BusinessException.runTaskMergeConflict(result.detail());
        }
        if (result.status() == MergeStatus.FAILED) {
            throw BusinessException.runTaskMergeFailed(result.detail());
        }

        log.info("Feature branch merged for repo: {}, from {} to {}", repoId, featureBranch, releaseBranch);
    }
}
