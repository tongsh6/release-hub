package io.releasehub.application.iteration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 迭代-仓库关联端口
 */
public interface IterationRepoPort {
    
    /**
     * 保存迭代-仓库关联（包含版本信息）
     */
    void saveWithVersion(String iterationKey, String repoId, String baseVersion, 
                         String devVersion, String targetVersion, String featureBranch,
                         String versionSource, Instant versionSyncedAt);
    
    /**
     * 获取迭代-仓库关联的版本信息
     */
    Optional<IterationRepoVersionInfo> getVersionInfo(String iterationKey, String repoId);
    
    /**
     * 获取迭代的所有仓库版本信息
     */
    List<IterationRepoVersionInfo> listVersionInfo(String iterationKey);
    
    /**
     * 更新版本信息
     */
    void updateVersion(String iterationKey, String repoId, String devVersion, 
                       String versionSource, Instant versionSyncedAt);
}
