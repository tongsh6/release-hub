package io.releasehub.infrastructure.run;

import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.iteration.IterationRepoVersionInfo;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.run.RunTaskContext;
import io.releasehub.application.run.RunTaskContextPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunTask;
import io.releasehub.domain.run.TargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * RunTaskContextPort 实现
 * 通过组合 RunPort、WindowIterationPort、IterationRepoPort 提供任务上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunTaskContextAdapter implements RunTaskContextPort {
    
    private final RunPort runPort;
    private final WindowIterationPort windowIterationPort;
    private final IterationRepoPort iterationRepoPort;
    
    @Override
    public Optional<RunTaskContext> getContext(RunTask task) {
        // 1. 获取 Run
        Optional<Run> runOpt = runPort.findById(task.getRunId().value());
        if (runOpt.isEmpty()) {
            log.warn("Run not found for task: {}", task.getId().value());
            return Optional.empty();
        }
        
        Run run = runOpt.get();
        
        // 2. 根据 targetType 和 targetId 找到对应的 RunItem
        String targetId = task.getTargetId();
        TargetType targetType = task.getTargetType();
        
        // 对于 REPOSITORY 类型，targetId 就是 repoId
        if (targetType == TargetType.REPOSITORY) {
            return findContextForRepo(run, targetId);
        }
        
        // 对于 ITERATION 类型，targetId 是 iterationKey
        if (targetType == TargetType.ITERATION) {
            return findContextForIteration(run, targetId);
        }
        
        log.warn("Unsupported target type: {} for task: {}", targetType, task.getId().value());
        return Optional.empty();
    }
    
    private Optional<RunTaskContext> findContextForRepo(Run run, String repoId) {
        // 从 RunItem 中找到包含该 repoId 的条目
        for (RunItem item : run.getItems()) {
            if (item.getRepo() != null && item.getRepo().value().equals(repoId)) {
                return buildContext(item, repoId);
            }
        }
        
        // 如果没有找到，尝试使用第一个 RunItem（兼容性）
        if (!run.getItems().isEmpty()) {
            RunItem firstItem = run.getItems().get(0);
            log.debug("Using first RunItem for repo {} context", repoId);
            return buildContext(firstItem, repoId);
        }
        
        log.warn("No RunItem found for repo: {}", repoId);
        return Optional.empty();
    }
    
    private Optional<RunTaskContext> findContextForIteration(Run run, String iterationKey) {
        // 从 RunItem 中找到包含该 iterationKey 的条目
        for (RunItem item : run.getItems()) {
            if (item.getIterationKey() != null && item.getIterationKey().value().equals(iterationKey)) {
                String repoId = item.getRepo() != null ? item.getRepo().value() : null;
                return buildContext(item, repoId);
            }
        }
        
        log.warn("No RunItem found for iteration: {}", iterationKey);
        return Optional.empty();
    }
    
    private Optional<RunTaskContext> buildContext(RunItem item, String repoId) {
        String windowKey = item.getWindowKey();
        String iterationKey = item.getIterationKey() != null ? item.getIterationKey().value() : null;
        
        RunTaskContext.RunTaskContextBuilder builder = RunTaskContext.builder()
                .windowKey(windowKey)
                .iterationKey(iterationKey)
                .repoId(repoId);
        
        // 3. 获取 releaseBranch
        if (windowKey != null && iterationKey != null) {
            try {
                String releaseBranch = windowIterationPort.getReleaseBranch(windowKey, iterationKey);
                builder.releaseBranch(releaseBranch);
            } catch (Exception e) {
                log.debug("Could not get release branch for window {} iteration {}: {}",
                        windowKey, iterationKey, e.getMessage());
            }
        }
        
        // 4. 获取版本信息
        if (iterationKey != null && repoId != null) {
            Optional<IterationRepoVersionInfo> versionInfo = iterationRepoPort.getVersionInfo(iterationKey, repoId);
            versionInfo.ifPresent(info -> {
                builder.featureBranch(info.getFeatureBranch())
                       .targetVersion(info.getTargetVersion())
                       .devVersion(info.getDevVersion())
                       .baseVersion(info.getBaseVersion());
            });
        }
        
        return Optional.of(builder.build());
    }
}
