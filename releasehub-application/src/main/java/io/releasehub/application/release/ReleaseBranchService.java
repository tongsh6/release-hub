package io.releasehub.application.release;

import io.releasehub.application.iteration.IterationAppService;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.port.out.GitLabBranchPort.MergeResult;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.MergeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Release 分支管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseBranchService {

    private final GitLabBranchPort gitLabBranchPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationAppService iterationAppService;
    private final IterationRepoPort iterationRepoPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final Clock clock = Clock.systemUTC();

    /**
     * 为迭代关联发布窗口时创建 release 分支并合并 feature
     */
    @Transactional
    public List<BranchOperationResult> createReleaseBranchAndMerge(String windowId, String windowKey, String iterationKey) {
        List<BranchOperationResult> results = new ArrayList<>();

        // 获取迭代信息
        Iteration iteration = iterationAppService.get(iterationKey);
        String releaseBranch = "release/" + windowKey;

        // 遍历迭代的所有仓库
        for (RepoId repoId : iteration.getRepos()) {
            Optional<CodeRepository> repoOpt = codeRepositoryPort.findById(repoId);
            if (repoOpt.isEmpty()) {
                log.warn("Repository not found: {}", repoId.value());
                continue;
            }

            CodeRepository repo = repoOpt.get();
            String repoUrl = repo.getCloneUrl();

            // 获取仓库的 feature 分支信息
            Optional<IterationRepoVersionInfo> versionInfo = iterationRepoPort.getVersionInfo(iterationKey, repoId.value());
            String featureBranch = versionInfo.map(IterationRepoVersionInfo::getFeatureBranch)
                                              .orElse("feature/" + iterationKey);

            try {
                // 1. 检查 release 分支是否存在，不存在则创建
                if (!gitLabBranchPort.branchExists(repoUrl, releaseBranch)) {
                    boolean created = gitLabBranchPort.createBranch(repoUrl, releaseBranch, repo.getDefaultBranch());
                    if (!created) {
                        results.add(BranchOperationResult.failed(repoId.value(), repo.getName(),
                                "Failed to create release branch: " + releaseBranch));
                        continue;
                    }
                    log.info("Created release branch {} for repo {}", releaseBranch, repo.getName());
                }

                // 2. 合并 feature 分支到 release 分支
                if (gitLabBranchPort.branchExists(repoUrl, featureBranch)) {
                    MergeResult mergeResult = gitLabBranchPort.mergeBranch(
                            repoUrl, featureBranch, releaseBranch,
                            "Merge " + featureBranch + " into " + releaseBranch);

                    if (mergeResult.status() == MergeStatus.SUCCESS) {
                        results.add(BranchOperationResult.success(repoId.value(), repo.getName()));
                    } else if (mergeResult.status() == MergeStatus.CONFLICT) {
                        results.add(BranchOperationResult.conflict(repoId.value(), repo.getName(), mergeResult.conflictInfo()));
                    } else {
                        results.add(BranchOperationResult.failed(repoId.value(), repo.getName(), mergeResult.conflictInfo()));
                    }
                } else {
                    log.info("Feature branch {} does not exist for repo {}, skipping merge", featureBranch, repo.getName());
                    results.add(BranchOperationResult.success(repoId.value(), repo.getName()));
                }

            } catch (Exception e) {
                log.error("Error processing repo {}: {}", repo.getName(), e.getMessage(), e);
                results.add(BranchOperationResult.failed(repoId.value(), repo.getName(), e.getMessage()));
            }
        }

        // 更新窗口-迭代关联记录
        windowIterationPort.updateReleaseBranch(windowId, iterationKey, releaseBranch, Instant.now(clock));

        return results;
    }

    /**
     * 分支操作结果
     */
    public record BranchOperationResult(
            String repoId,
            String repoName,
            MergeStatus status,
            String message
    ) {
        public static BranchOperationResult success(String repoId, String repoName) {
            return new BranchOperationResult(repoId, repoName, MergeStatus.SUCCESS, null);
        }

        public static BranchOperationResult conflict(String repoId, String repoName, String conflictInfo) {
            return new BranchOperationResult(repoId, repoName, MergeStatus.CONFLICT, conflictInfo);
        }

        public static BranchOperationResult failed(String repoId, String repoName, String error) {
            return new BranchOperationResult(repoId, repoName, MergeStatus.FAILED, error);
        }
    }
}
