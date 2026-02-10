package io.releasehub.application.run;

import lombok.Builder;
import lombok.Getter;

/**
 * 运行任务上下文，包含执行器所需的所有上下文信息
 */
@Getter
@Builder
public final class RunTaskContext {
    
    private RunTaskContext(String windowKey, String iterationKey, String repoId,
                           String featureBranch, String releaseBranch,
                           String targetVersion, String devVersion, String baseVersion) {
        this.windowKey = windowKey;
        this.iterationKey = iterationKey;
        this.repoId = repoId;
        this.featureBranch = featureBranch;
        this.releaseBranch = releaseBranch;
        this.targetVersion = targetVersion;
        this.devVersion = devVersion;
        this.baseVersion = baseVersion;
    }
    /**
     * 发布窗口 key（如 RW-2024W01）
     */
    private final String windowKey;
    
    /**
     * 迭代 key（如 ITER-xxx）
     */
    private final String iterationKey;
    
    /**
     * 仓库 ID
     */
    private final String repoId;
    
    /**
     * Feature 分支名（如 feature/ITER-xxx）
     */
    private final String featureBranch;
    
    /**
     * Release 分支名（如 release/RW-2024W01）
     */
    private final String releaseBranch;
    
    /**
     * 目标版本号（如 1.2.0）
     */
    private final String targetVersion;
    
    /**
     * 开发版本号（如 1.2.0-SNAPSHOT）
     */
    private final String devVersion;
    
    /**
     * 基础版本号（如 1.1.0）
     */
    private final String baseVersion;
}
