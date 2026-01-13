package io.releasehub.application.release.executors;

import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.release.AbstractRunTaskExecutor;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.MergeStatus;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.RunTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 合并 release 分支到 master 任务执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MergeReleaseToMasterExecutor extends AbstractRunTaskExecutor {
    
    private final GitLabBranchPort gitLabBranchPort;
    private final CodeRepositoryPort codeRepositoryPort;
    
    @Override
    public RunTaskType getTaskType() {
        return RunTaskType.MERGE_RELEASE_TO_MASTER;
    }
    
    @Override
    public void execute(RunTask task) throws Exception {
        String repoId = task.getTargetId();
        log.info("Merging release to master for repo: {}", repoId);
        
        CodeRepository repo = codeRepositoryPort.findById(RepoId.of(repoId))
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repoId));
        
        // TODO: 获取 release 分支名
        String releaseBranch = "release/RW-xxx"; // 需要从上下文获取
        String masterBranch = repo.getDefaultBranch();
        
        GitLabBranchPort.MergeResult result = gitLabBranchPort.mergeBranch(
                repo.getCloneUrl(), releaseBranch, masterBranch,
                "Merge " + releaseBranch + " into " + masterBranch);
        
        if (result.status() == MergeStatus.CONFLICT) {
            throw new RuntimeException("Merge conflict: " + result.conflictInfo());
        }
        
        if (result.status() == MergeStatus.FAILED) {
            throw new RuntimeException("Merge failed: " + result.conflictInfo());
        }
        
        log.info("Release merged to master for repo: {}", repoId);
    }
}
