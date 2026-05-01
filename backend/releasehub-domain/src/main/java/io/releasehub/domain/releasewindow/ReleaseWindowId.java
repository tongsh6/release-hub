package io.releasehub.domain.releasewindow;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;

import java.util.UUID;

/**
 * 发布窗口标识符
 */
public record ReleaseWindowId(String value) implements EntityId {

    public ReleaseWindowId {
        if (value == null || value.isBlank()) {
            throw ValidationException.of(ErrorCode.RW_ID_INVALID);
        }
    }

    /**
     * 生成新的 ReleaseWindowId
     */
    public static ReleaseWindowId newId() {
        return new ReleaseWindowId(UUID.randomUUID().toString());
    }

    /**
     * 从字符串创建 ReleaseWindowId
     */
    public static ReleaseWindowId of(String value) {
        return new ReleaseWindowId(value);
    }
}
