package io.releasehub.domain.repo;

import java.util.UUID;

public record RepoId(String value) {
    public RepoId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RepoId cannot be null or empty");
        }
    }

    public static RepoId newId() {
        return new RepoId(UUID.randomUUID().toString());
    }
}
