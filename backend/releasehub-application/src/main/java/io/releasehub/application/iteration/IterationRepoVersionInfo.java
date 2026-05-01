package io.releasehub.application.iteration;

import io.releasehub.domain.version.VersionSource;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 迭代-仓库版本信息
 */
@Data
@Builder
public class IterationRepoVersionInfo {
    private String repoId;
    private String repoName;
    private String baseVersion;      // 关联时 master 版本
    private String devVersion;       // feature 分支开发版本
    private String targetVersion;    // 发布目标版本
    private String featureBranch;    // feature 分支名
    private VersionSource versionSource;
    private Instant versionSyncedAt;
}
