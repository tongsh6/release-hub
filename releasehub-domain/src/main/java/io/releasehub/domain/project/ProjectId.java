package io.releasehub.domain.project;

import java.util.UUID;

public record ProjectId(String value) {
    public ProjectId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ProjectId cannot be null or empty");
        }
    }

    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID().toString());
    }
}
