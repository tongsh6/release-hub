package io.releasehub.domain.version;

import java.util.Objects;

/**
 * 版本策略作用域值对象。
 */
public class VersionPolicyScope {

    public enum ScopeLevel {
        GLOBAL,
        PROJECT,
        SUB_PROJECT
    }

    private final ScopeLevel level;
    private final String projectId;
    private final String subProjectId;

    private VersionPolicyScope(ScopeLevel level, String projectId, String subProjectId) {
        this.level = Objects.requireNonNull(level, "scope level must not be null");
        this.projectId = projectId;
        this.subProjectId = subProjectId;
    }

    public static VersionPolicyScope global() {
        return new VersionPolicyScope(ScopeLevel.GLOBAL, null, null);
    }

    public static VersionPolicyScope project(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required for PROJECT scope");
        }
        return new VersionPolicyScope(ScopeLevel.PROJECT, projectId, null);
    }

    public static VersionPolicyScope subProject(String projectId, String subProjectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required for SUB_PROJECT scope");
        }
        if (subProjectId == null || subProjectId.isBlank()) {
            throw new IllegalArgumentException("subProjectId is required for SUB_PROJECT scope");
        }
        return new VersionPolicyScope(ScopeLevel.SUB_PROJECT, projectId, subProjectId);
    }

    public ScopeLevel getLevel() { return level; }
    public String getProjectId() { return projectId; }
    public String getSubProjectId() { return subProjectId; }

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
}
