package io.releasehub.application.release;

import io.releasehub.application.iteration.IterationAppService;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.port.out.GitLabBranchPort.MergeResult;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.MergeStatus;
import io.releasehub.domain.window.WindowIteration;
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
 * 代码合并服务
 * 用于将 feature 分支的最新代码合并到 release 分支
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeMergeService {
    
    private final GitLabBranchPort gitLabBranchPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationAppService iterationAppService;
    private final IterationRepoPort iterationRepoPort;
    private final CodeRepositoryPort codeRepositoryPort;
    private final Clock clock = Clock.systemUTC();
    
    /**
     * 合并指定迭代的所有 feature 分支到 release 分支
     */
    @Transactional
    public List<CodeMergeResult> mergeFeatureToRelease(String windowId, String iterationKey) {
        List<CodeMergeResult> results = new ArrayList<>();
        
        // 获取窗口-迭代关联信息，获取 release 分支名
        Optional<WindowIteration> wiOpt = windowIterationPort.findByWindowIdAndIterationKey(
                ReleaseWindowId.of(windowId), IterationKey.of(iterationKey));
        
        if (wiOpt.isEmpty()) {
            log.warn("Window-Iteration relation not found: windowId={}, iterationKey={}", windowId, iterationKey);
            return results;
        }
        
        WindowIteration wi = wiOpt.get();
        String releaseBranch = windowIterationPort.getReleaseBranch(windowId, iterationKey);
        
        if (releaseBranch == null || releaseBranch.isEmpty()) {
            log.warn("Release branch not set for window-iteration: {}-{}", windowId, iterationKey);
            return results;
        }
        
        // 获取迭代信息
        Iteration iteration = iterationAppService.get(iterationKey);
        
        // 遍历迭代的所有仓库
        for (RepoId repoId : iteration.getRepos()) {
            Optional<CodeRepository> repoOpt = codeRepositoryPort.findById(repoId);
            if (repoOpt.isEmpty()) {
                continue;
            }
            
            CodeRepository repo = repoOpt.get();
            String repoUrl = repo.getCloneUrl();
            
            // 获取仓库的 feature 分支信息
            Optional<IterationRepoVersionInfo> versionInfo = iterationRepoPort.getVersionInfo(iterationKey, repoId.value());
            String featureBranch = versionInfo.map(IterationRepoVersionInfo::getFeatureBranch)
                                              .orElse("feature/" + iterationKey);
            
            try {
                // 检查 feature 分支是否存在
                if (!gitLabBranchPort.branchExists(repoUrl, featureBranch)) {
                    log.info("Feature branch {} does not exist for repo {}", featureBranch, repo.getName());
                    results.add(CodeMergeResult.skipped(repoId.value(), repo.getName(), featureBranch, releaseBranch, 
                            "Feature branch does not exist"));
                    continue;
                }
                
                // 检查 release 分支是否存在
                if (!gitLabBranchPort.branchExists(repoUrl, releaseBranch)) {
                    log.warn("Release branch {} does not exist for repo {}", releaseBranch, repo.getName());
                    results.add(CodeMergeResult.failed(repoId.value(), repo.getName(), featureBranch, releaseBranch, 
                            "Release branch does not exist"));
                    continue;
                }
                
                // 执行合并
                MergeResult mergeResult = gitLabBranchPort.mergeBranch(
                        repoUrl, featureBranch, releaseBranch,
                        "Merge " + featureBranch + " into " + releaseBranch);
                
                Instant now = Instant.now(clock);
                
                if (mergeResult.status() == MergeStatus.SUCCESS) {
                    results.add(CodeMergeResult.success(repoId.value(), repo.getName(), featureBranch, releaseBranch, now));
                } else if (mergeResult.status() == MergeStatus.CONFLICT) {
                    results.add(CodeMergeResult.conflict(repoId.value(), repo.getName(), featureBranch, releaseBranch, 
                            mergeResult.conflictInfo()));
                } else {
                    results.add(CodeMergeResult.failed(repoId.value(), repo.getName(), featureBranch, releaseBranch, 
                            mergeResult.conflictInfo()));
                }
                
            } catch (Exception e) {
                log.error("Error merging repo {}: {}", repo.getName(), e.getMessage(), e);
                results.add(CodeMergeResult.failed(repoId.value(), repo.getName(), featureBranch, releaseBranch, 
                        e.getMessage()));
            }
        }
        
        // 更新最后合并时间
        windowIterationPort.updateLastMergeAt(windowId, iterationKey, Instant.now(clock));
        
        return results;
    }
    
    /**
     * 批量合并：发布窗口下所有迭代的 feature 分支合并到 release
     */
    @Transactional
    public List<CodeMergeResult> mergeAllFeaturesToRelease(String windowId) {
        List<CodeMergeResult> allResults = new ArrayList<>();
        
        // 获取窗口下所有关联的迭代
        List<WindowIteration> iterations = windowIterationPort.listByWindow(ReleaseWindowId.of(windowId));
        
        for (WindowIteration wi : iterations) {
            List<CodeMergeResult> results = mergeFeatureToRelease(windowId, wi.getIterationKey().value());
            allResults.addAll(results);
        }
        
        return allResults;
    }
    
    /**
     * 代码合并结果
     */
    public record CodeMergeResult(
            String repoId,
            String repoName,
            String sourceBranch,
            String targetBranch,
            MergeStatus status,
            String message,
            Instant mergedAt
    ) {
        public static CodeMergeResult success(String repoId, String repoName, 
                                               String sourceBranch, String targetBranch, Instant mergedAt) {
            return new CodeMergeResult(repoId, repoName, sourceBranch, targetBranch, 
                    MergeStatus.SUCCESS, null, mergedAt);
        }
        
        public static CodeMergeResult conflict(String repoId, String repoName,
                                                String sourceBranch, String targetBranch, String conflictInfo) {
            return new CodeMergeResult(repoId, repoName, sourceBranch, targetBranch, 
                    MergeStatus.CONFLICT, conflictInfo, null);
        }
        
        public static CodeMergeResult failed(String repoId, String repoName,
                                              String sourceBranch, String targetBranch, String error) {
            return new CodeMergeResult(repoId, repoName, sourceBranch, targetBranch, 
                    MergeStatus.FAILED, error, null);
        }
        
        public static CodeMergeResult skipped(String repoId, String repoName,
                                               String sourceBranch, String targetBranch, String reason) {
            return new CodeMergeResult(repoId, repoName, sourceBranch, targetBranch, 
                    MergeStatus.SUCCESS, reason, null); // 跳过视为成功
        }
    }
}
