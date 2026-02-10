package io.releasehub.domain.group;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;

import java.util.UUID;

/**
 * 分组标识符
 */
public record GroupId(String value) implements EntityId {

    public GroupId {
        if (value == null || value.isBlank()) {
            throw ValidationException.of(ErrorCode.GROUP_ID_INVALID);
        }
    }

    /**
     * 生成新的 GroupId
     */
    public static GroupId newId() {
        return new GroupId(UUID.randomUUID().toString());
    }

    /**
     * 从字符串创建 GroupId
     */
    public static GroupId of(String value) {
        return new GroupId(value);
    }
}
