package io.releasehub.application.iteration;

import java.time.Instant;

public record IterationRepoDetailView(
        String repoId,
        String repoName,
        String cloneUrl,
        String groupCode,
        String defaultBranch,
        String repoType,
        Boolean monoRepo,
        String branchCreationMode,
        String featureBranch,
        String baseVersion,
        String devVersion,
        String targetVersion,
        String versionSource,
        Instant versionSyncedAt
) {
}
