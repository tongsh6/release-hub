package io.releasehub.domain.project;

import io.releasehub.common.exception.BizException;
import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;

@Getter
public class Project extends BaseEntity<ProjectId> {
    private final String description;
    private String name;
    private ProjectStatus status;

    public Project(ProjectId id, String name, String description, ProjectStatus status, Instant createdAt, Instant updatedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.name = name;
        this.description = description;
        this.status = status;
    }

    private Project(ProjectId id, String name, String description, Instant now) {
        super(id, now);
        validateName(name);
        validateDescription(description);
        this.name = name;
        this.description = description;
        this.status = ProjectStatus.ACTIVE;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException("PJ_NAME_REQUIRED", "Project name is required");
        }
        if (name.length() > 128) {
            throw new BizException("PJ_NAME_TOO_LONG", "Project name is too long (max 128)");
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > 512) {
            throw new BizException("PJ_DESC_TOO_LONG", "Project description is too long (max 512)");
        }
    }

    public static Project create(String name, String description, Instant now) {
        return new Project(ProjectId.newId(), name, description, now);
    }

    public void rename(String name, Instant now) {
        validateName(name);
        this.name = name;
        touch(now);
    }

    public void archive(Instant now) {
        if (this.status == ProjectStatus.ARCHIVED) {
            return; // Idempotent
        }
        this.status = ProjectStatus.ARCHIVED;
        touch(now);
    }
}
