package io.releasehub.domain.group;

import java.util.UUID;

public record GroupId(String value) {
    public GroupId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GroupId cannot be null or empty");
        }
    }

    public static GroupId newId() {
        return new GroupId(UUID.randomUUID().toString());
    }
}

