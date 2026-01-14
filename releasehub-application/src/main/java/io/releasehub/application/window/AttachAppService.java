package io.releasehub.application.window;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachAppService {
    private final ReleaseWindowPort releaseWindowPort;
    private final IterationPort iterationPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationRepoPort iterationRepoPort;
    private final GitLabBranchPort gitLabBranchPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public List<WindowIteration> attach(String windowId, List<String> iterationKeys) {
        ReleaseWindow releaseWindow = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        Instant now = Instant.now(clock);
        
        return iterationKeys.stream()
                .map(IterationKey::of)
                .map(iterationKey -> {
                    Iteration iteration = iterationPort.findByKey(iterationKey).orElseThrow();
                    
                    // 创建关联记录
                    WindowIteration wi = windowIterationPort.attach(ReleaseWindowId.of(windowId), iterationKey, now);
                    
                    // 为每个仓库创建 release 分支并合并 feature 分支
                    for (RepoId repoId : iteration.getRepos()) {
                        try {
                            setupReleaseBranchForRepo(releaseWindow, iteration, iterationKey, repoId, now);
                        } catch (Exception e) {
                            log.error("Failed to setup release branch for repo {} in window {}: {}", 
                                    repoId.value(), windowId, e.getMessage());
                        }
                    }
                    
                    return wi;
                })
                .toList();
    }
    
    /**
     * 为仓库设置 release 分支：创建分支并合并 feature 分支
     */
    private void setupReleaseBranchForRepo(ReleaseWindow releaseWindow, Iteration iteration, 
            IterationKey iterationKey, RepoId repoId, Instant now) {
        // 生成 release 分支名
        String releaseBranch = "release/" + releaseWindow.getWindowKey();
        
        // 获取 feature 分支名
        String featureBranch = iterationRepoPort.getVersionInfo(iterationKey.value(), repoId.value())
                .map(info -> info.getFeatureBranch())
                .orElse("feature/" + iterationKey.value());
        
        // 1. 创建 release 分支（如果不存在）
        boolean branchCreated = gitLabBranchPort.createBranch(repoId.value(), releaseBranch, "master");
        if (branchCreated) {
            log.info("Created release branch {} for repo {}", releaseBranch, repoId.value());
        }
        
        // 2. 将 feature 分支合并到 release 分支
        var mergeResult = gitLabBranchPort.mergeBranch(
                repoId.value(), 
                featureBranch, 
                releaseBranch, 
                "Merge " + featureBranch + " to " + releaseBranch + " for iteration " + iteration.getName());
        
        if (mergeResult.status() == io.releasehub.domain.run.MergeStatus.SUCCESS) {
            log.info("Merged feature branch {} to release branch {} for repo {}", 
                    featureBranch, releaseBranch, repoId.value());
            // 更新合并时间
            windowIterationPort.updateLastMergeAt(
                    releaseWindow.getId().value(), 
                    iterationKey.value(), 
                    now);
        } else {
            log.warn("Failed to merge feature branch {} to release branch {} for repo {}: {}", 
                    featureBranch, releaseBranch, repoId.value(), mergeResult.conflictInfo());
        }
        
        // 3. 记录 release 分支名
        windowIterationPort.updateReleaseBranch(
                releaseWindow.getId().value(), 
                iterationKey.value(), 
                releaseBranch, 
                now);
    }

    @Transactional
    public void detach(String windowId, String iterationKey) {
        releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        iterationPort.findByKey(IterationKey.of(iterationKey)).orElseThrow();
        windowIterationPort.detach(ReleaseWindowId.of(windowId), IterationKey.of(iterationKey));
    }

    public List<WindowIteration> list(String windowId) {
        releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        return windowIterationPort.listByWindow(ReleaseWindowId.of(windowId));
    }

    public PageResult<WindowIteration> listPaged(String windowId, int page, int size) {
        releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        return windowIterationPort.listByWindowPaged(ReleaseWindowId.of(windowId), page, size);
    }

    /**
     * 为迭代创建 release 分支
     */
    @Transactional
    public void createReleaseBranchForIteration(String windowId, String iterationKeyStr) {
        ReleaseWindow releaseWindow = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        IterationKey iterationKey = IterationKey.of(iterationKeyStr);
        Iteration iteration = iterationPort.findByKey(iterationKey).orElseThrow();
        Instant now = Instant.now(clock);
        
        String releaseBranch = "release/" + releaseWindow.getWindowKey();
        
        for (RepoId repoId : iteration.getRepos()) {
            try {
                boolean created = gitLabBranchPort.createBranch(repoId.value(), releaseBranch, "master");
                if (created) {
                    log.info("Created release branch {} for repo {}", releaseBranch, repoId.value());
                }
            } catch (Exception e) {
                log.error("Failed to create release branch for repo {}: {}", repoId.value(), e.getMessage());
            }
        }
        
        windowIterationPort.updateReleaseBranch(windowId, iterationKeyStr, releaseBranch, now);
    }

    /**
     * 将迭代的所有 feature 分支合并到 release 分支
     */
    @Transactional
    public void mergeFeatureToRelease(String windowId, String iterationKeyStr) {
        ReleaseWindow releaseWindow = releaseWindowPort.findById(ReleaseWindowId.of(windowId)).orElseThrow();
        IterationKey iterationKey = IterationKey.of(iterationKeyStr);
        Iteration iteration = iterationPort.findByKey(iterationKey).orElseThrow();
        Instant now = Instant.now(clock);
        
        String releaseBranch = windowIterationPort.getReleaseBranch(windowId, iterationKeyStr);
        if (releaseBranch == null) {
            releaseBranch = "release/" + releaseWindow.getWindowKey();
        }
        
        for (RepoId repoId : iteration.getRepos()) {
            try {
                String featureBranch = iterationRepoPort.getVersionInfo(iterationKey.value(), repoId.value())
                        .map(info -> info.getFeatureBranch())
                        .orElse("feature/" + iterationKey.value());
                
                var mergeResult = gitLabBranchPort.mergeBranch(
                        repoId.value(),
                        featureBranch,
                        releaseBranch,
                        "Merge " + featureBranch + " to " + releaseBranch + " for iteration " + iteration.getName());
                
                if (mergeResult.status() == io.releasehub.domain.run.MergeStatus.SUCCESS) {
                    log.info("Merged feature branch {} to release branch {} for repo {}", 
                            featureBranch, releaseBranch, repoId.value());
                }
            } catch (Exception e) {
                log.error("Failed to merge feature branch for repo {}: {}", repoId.value(), e.getMessage());
            }
        }
        
        windowIterationPort.updateLastMergeAt(windowId, iterationKeyStr, now);
    }
}
