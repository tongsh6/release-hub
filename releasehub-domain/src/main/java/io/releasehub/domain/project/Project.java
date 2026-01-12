package io.releasehub.domain.project;

import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;

@Getter
public class Project extends BaseEntity<ProjectId> {
    private final String description;
    private String name;
    private ProjectStatus status;

    public Project(ProjectId id, String name, String description, ProjectStatus status, Instant createdAt, Instant updatedAt, long version) {
        super(id, createdAt, updatedAt, version);
        this.name = name;
        this.description = description;
        this.status = status;
    }

    public static Project rehydrate(ProjectId id, String name, String description, ProjectStatus status, Instant createdAt, Instant updatedAt, long version) {
        return new Project(id, name, description, status, createdAt, updatedAt, version);
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
            throw ValidationException.projectNameRequired();
        }
        if (name.length() > 128) {
            throw ValidationException.projectNameTooLong(128);
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > 512) {
            throw ValidationException.projectDescTooLong(512);
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
