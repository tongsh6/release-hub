package io.releasehub.domain.project;

import io.releasehub.common.exception.BizException;
import lombok.Getter;

import java.time.Instant;

@Getter
public class Project {
    private final ProjectId id;
    private final String description;
    private final Instant createdAt;
    private String name;
    private ProjectStatus status;
    private Instant updatedAt;

    // Constructor for reconstruction (e.g. from persistence)
    public Project(ProjectId id, String name, String description, ProjectStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private Project(String name, String description, Instant now) {
        validateName(name);
        validateDescription(description);
        this.id = ProjectId.newId();
        this.name = name;
        this.description = description;
        this.status = ProjectStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
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
        return new Project(name, description, now);
    }

    public void rename(String name, Instant now) {
        validateName(name);
        this.name = name;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        if (this.status == ProjectStatus.ARCHIVED) {
            return; // Idempotent
        }
        this.status = ProjectStatus.ARCHIVED;
        this.updatedAt = now;
    }
}
