package io.releasehub.domain.group;

import io.releasehub.common.exception.ValidationException;

import java.util.UUID;

public record GroupId(String value) {
    public GroupId {
        if (value == null || value.isBlank()) {
            throw ValidationException.groupIdInvalid();
        }
    }

    public static GroupId newId() {
        return new GroupId(UUID.randomUUID().toString());
    }
}
