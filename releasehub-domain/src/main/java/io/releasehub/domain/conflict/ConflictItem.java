package io.releasehub.domain.conflict;

import io.releasehub.domain.version.ConflictType;
import java.util.Objects;

/**
 * 冲突项值对象 — 描述单个冲突详情
 */
public class ConflictItem {
    private final String repoId;
    private final String repoName;
    private final String iterationKey;
    private final ConflictType conflictType;
    private final String sourceBranch;
    private final String targetBranch;
    private final String systemVersion;
    private final String repoVersion;
    private final String message;
    private final String suggestion;

    private ConflictItem(Builder builder) {
        this.repoId = Objects.requireNonNull(builder.repoId);
        this.repoName = builder.repoName;
        this.iterationKey = builder.iterationKey;
        this.conflictType = Objects.requireNonNull(builder.conflictType);
        this.sourceBranch = builder.sourceBranch;
        this.targetBranch = builder.targetBranch;
        this.systemVersion = builder.systemVersion;
        this.repoVersion = builder.repoVersion;
        this.message = builder.message;
        this.suggestion = builder.suggestion;
    }

    public static ConflictItem versionMismatch(String repoId, String repoName, String iterationKey,
                                                String systemVersion, String repoVersion) {
        return new Builder(repoId, ConflictType.MISMATCH)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .message("System version (" + systemVersion + ") does not match repo version (" + repoVersion + ")")
                .suggestion("Resolve the version inconsistency via version sync")
                .build();
    }

    public static ConflictItem repoAhead(String repoId, String repoName, String iterationKey,
                                          String systemVersion, String repoVersion) {
        return new Builder(repoId, ConflictType.REPO_AHEAD)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .message("Repo version (" + repoVersion + ") is ahead of system record (" + systemVersion + ")")
                .suggestion("Sync repo version to system")
                .build();
    }

    public static ConflictItem systemAhead(String repoId, String repoName, String iterationKey,
                                            String systemVersion, String repoVersion) {
        return new Builder(repoId, ConflictType.SYSTEM_AHEAD)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .message("System version (" + systemVersion + ") is ahead of repo version (" + repoVersion + ")")
                .suggestion("Sync system version to repo")
                .build();
    }

    public static ConflictItem branchExists(String repoId, String repoName, String iterationKey,
                                             String branchName) {
        return new Builder(repoId, ConflictType.BRANCH_EXISTS)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .sourceBranch(branchName)
                .message("Branch " + branchName + " already exists")
                .suggestion("Delete or archive the existing branch before retrying")
                .build();
    }

    public static ConflictItem branchNoncompliant(String repoId, String repoName, String iterationKey,
                                                   String branchName) {
        return new Builder(repoId, ConflictType.BRANCH_NONCOMPLIANT)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .sourceBranch(branchName)
                .message("Branch name '" + branchName + "' does not comply with branch rules")
                .suggestion("Rename the branch to comply with BranchRule")
                .build();
    }

    public static ConflictItem crossRepoVersionMismatch(String repoId, String repoName, String iterationKey,
                                                         String version, String otherRepoId, String otherVersion) {
        return new Builder(repoId, ConflictType.CROSS_REPO_VERSION_MISMATCH)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(version)
                .repoVersion(otherVersion)
                .message("Repo " + repoId + " version (" + version + ") mismatches " + otherRepoId + " (" + otherVersion + ")")
                .suggestion("Align target versions across all repos in this iteration")
                .build();
    }

    public static ConflictItem mergeConflict(String repoId, String repoName, String iterationKey,
                                              String sourceBranch, String targetBranch, String detail) {
        return new Builder(repoId, ConflictType.MERGE_CONFLICT)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .sourceBranch(sourceBranch)
                .targetBranch(targetBranch)
                .message(sourceBranch + " → " + targetBranch + " has merge conflict" + (detail != null ? ": " + detail : ""))
                .suggestion("Resolve conflicts manually on the Git platform")
                .build();
    }

    // Getters
    public String getRepoId() { return repoId; }
    public String getRepoName() { return repoName; }
    public String getIterationKey() { return iterationKey; }
    public ConflictType getConflictType() { return conflictType; }
    public String getSourceBranch() { return sourceBranch; }
    public String getTargetBranch() { return targetBranch; }
    public String getSystemVersion() { return systemVersion; }
    public String getRepoVersion() { return repoVersion; }
    public String getMessage() { return message; }
    public String getSuggestion() { return suggestion; }

    public static class Builder {
        private final String repoId;
        private final ConflictType conflictType;
        private String repoName;
        private String iterationKey;
        private String sourceBranch;
        private String targetBranch;
        private String systemVersion;
        private String repoVersion;
        private String message;
        private String suggestion;

        public Builder(String repoId, ConflictType conflictType) {
            this.repoId = repoId;
            this.conflictType = conflictType;
        }

        public Builder repoName(String v) { this.repoName = v; return this; }
        public Builder iterationKey(String v) { this.iterationKey = v; return this; }
        public Builder sourceBranch(String v) { this.sourceBranch = v; return this; }
        public Builder targetBranch(String v) { this.targetBranch = v; return this; }
        public Builder systemVersion(String v) { this.systemVersion = v; return this; }
        public Builder repoVersion(String v) { this.repoVersion = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder suggestion(String v) { this.suggestion = v; return this; }

        public ConflictItem build() { return new ConflictItem(this); }
    }
}
