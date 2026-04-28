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
                .message("系统版本(" + systemVersion + ")与仓库版本(" + repoVersion + ")不一致")
                .suggestion("请使用版本同步功能解决冲突")
                .build();
    }

    public static ConflictItem repoAhead(String repoId, String repoName, String iterationKey,
                                          String systemVersion, String repoVersion) {
        return new Builder(repoId, ConflictType.REPO_AHEAD)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .message("仓库版本(" + repoVersion + ")高于系统记录(" + systemVersion + ")")
                .suggestion("请同步仓库版本到系统")
                .build();
    }

    public static ConflictItem systemAhead(String repoId, String repoName, String iterationKey,
                                            String systemVersion, String repoVersion) {
        return new Builder(repoId, ConflictType.SYSTEM_AHEAD)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(systemVersion)
                .repoVersion(repoVersion)
                .message("系统版本(" + systemVersion + ")高于仓库版本(" + repoVersion + ")")
                .suggestion("请同步系统版本到仓库")
                .build();
    }

    public static ConflictItem branchExists(String repoId, String repoName, String iterationKey,
                                             String branchName) {
        return new Builder(repoId, ConflictType.BRANCH_EXISTS)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .sourceBranch(branchName)
                .message("分支 " + branchName + " 已存在")
                .suggestion("请删除或归档已存在的分支后重试")
                .build();
    }

    public static ConflictItem branchNoncompliant(String repoId, String repoName, String iterationKey,
                                                   String branchName) {
        return new Builder(repoId, ConflictType.BRANCH_NONCOMPLIANT)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .sourceBranch(branchName)
                .message("分支名 " + branchName + " 不符合命名规则")
                .suggestion("请修改分支名以符合 BranchRule 规则")
                .build();
    }

    public static ConflictItem crossRepoVersionMismatch(String repoId, String repoName, String iterationKey,
                                                         String version, String otherRepoId, String otherVersion) {
        return new Builder(repoId, ConflictType.CROSS_REPO_VERSION_MISMATCH)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .systemVersion(version)
                .repoVersion(otherVersion)
                .message("仓库 " + repoId + " 版本(" + version + ")与 " + otherRepoId + " 版本(" + otherVersion + ")不一致")
                .suggestion("请统一迭代内所有仓库的目标版本")
                .build();
    }

    public static ConflictItem mergeConflict(String repoId, String repoName, String iterationKey,
                                              String sourceBranch, String targetBranch, String detail) {
        return new Builder(repoId, ConflictType.MERGE_CONFLICT)
                .repoName(repoName)
                .iterationKey(iterationKey)
                .sourceBranch(sourceBranch)
                .targetBranch(targetBranch)
                .message(sourceBranch + " → " + targetBranch + " 存在合并冲突: " + (detail != null ? detail : ""))
                .suggestion("请手动解决冲突")
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
