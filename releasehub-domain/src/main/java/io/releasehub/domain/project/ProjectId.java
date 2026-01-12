package io.releasehub.domain.project;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;

import java.util.UUID;

/**
 * 项目标识符
 */
public record ProjectId(String value) implements EntityId {

    public ProjectId {
        if (value == null || value.isBlank()) {
            throw ValidationException.of(ErrorCode.PROJECT_ID_INVALID);
        }
    }

    /**
     * 生成新的 ProjectId
     */
    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID().toString());
    }

    /**
     * 从字符串创建 ProjectId
     */
    public static ProjectId of(String value) {
        return new ProjectId(value);
    }
}
