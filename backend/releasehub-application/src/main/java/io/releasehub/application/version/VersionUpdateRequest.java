package io.releasehub.application.version;

import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.version.BuildTool;

/**
 * 版本更新请求
 */
public record VersionUpdateRequest(
        RepoId repoId,
        String branchName,
        String repoPath,
        BuildTool buildTool,
        String targetVersion,
        String pomPath,
        String gradlePropertiesPath
) {
    /**
     * 创建 Maven 版本更新请求
     */
    public static VersionUpdateRequest forMaven(RepoId repoId, String branchName, String repoPath, String targetVersion, String pomPath) {
        return new VersionUpdateRequest(repoId, branchName, repoPath, BuildTool.MAVEN, targetVersion, pomPath, null);
    }

    /**
     * 创建 Gradle 版本更新请求
     */
    public static VersionUpdateRequest forGradle(RepoId repoId, String branchName, String repoPath, String targetVersion, String gradlePropertiesPath) {
        return new VersionUpdateRequest(repoId, branchName, repoPath, BuildTool.GRADLE, targetVersion, null, gradlePropertiesPath);
    }
}

