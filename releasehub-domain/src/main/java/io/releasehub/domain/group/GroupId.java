package io.releasehub.domain.group;

import io.releasehub.common.exception.BizException;
import java.util.UUID;

public record GroupId(String value) {
    public GroupId {
        if (value == null || value.isBlank()) {
            throw new BizException("GROUP_ID_INVALID", "GroupId cannot be null or empty");
        }
    }

    public static GroupId newId() {
        return new GroupId(UUID.randomUUID().toString());
    }
}

