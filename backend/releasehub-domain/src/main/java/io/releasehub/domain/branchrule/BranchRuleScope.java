package io.releasehub.domain.branchrule;

import java.util.Objects;

/**
 * 分支规则作用域值对象
 */
public class BranchRuleScope {

    public enum ScopeLevel {
        GLOBAL,
        PROJECT,
        SUB_PROJECT
    }

    private final ScopeLevel level;
    private final String projectId;
    private final String subProjectId;

    private BranchRuleScope(ScopeLevel level, String projectId, String subProjectId) {
        this.level = Objects.requireNonNull(level, "scope level must not be null");
        this.projectId = projectId;
        this.subProjectId = subProjectId;
    }

    public static BranchRuleScope global() {
        return new BranchRuleScope(ScopeLevel.GLOBAL, null, null);
    }

    public static BranchRuleScope project(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required for PROJECT scope");
        }
        return new BranchRuleScope(ScopeLevel.PROJECT, projectId, null);
    }

    public static BranchRuleScope subProject(String projectId, String subProjectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required for SUB_PROJECT scope");
        }
        if (subProjectId == null || subProjectId.isBlank()) {
            throw new IllegalArgumentException("subProjectId is required for SUB_PROJECT scope");
        }
        return new BranchRuleScope(ScopeLevel.SUB_PROJECT, projectId, subProjectId);
    }

    public ScopeLevel getLevel() { return level; }
    public String getProjectId() { return projectId; }
    public String getSubProjectId() { return subProjectId; }

    public boolean isGlobal() { return level == ScopeLevel.GLOBAL; }

    public boolean matches(String projectId, String subProjectId) {
        return switch (level) {
            case GLOBAL -> true;
            case PROJECT -> Objects.equals(this.projectId, projectId);
            case SUB_PROJECT -> Objects.equals(this.projectId, projectId)
                    && Objects.equals(this.subProjectId, subProjectId);
        };
    }

    public int specificity() {
        return switch (level) {
            case GLOBAL -> 0;
            case PROJECT -> 1;
            case SUB_PROJECT -> 2;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BranchRuleScope that)) return false;
        return level == that.level
                && Objects.equals(projectId, that.projectId)
                && Objects.equals(subProjectId, that.subProjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, projectId, subProjectId);
    }
}
