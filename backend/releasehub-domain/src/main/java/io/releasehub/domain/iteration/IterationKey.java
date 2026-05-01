package io.releasehub.domain.iteration;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;

/**
 * 迭代标识符
 * <p>
 * 注意：IterationKey 使用业务 key，不是自动生成的 UUID
 */
public record IterationKey(String value) implements EntityId {

    public IterationKey {
        if (value == null || value.isBlank()) {
            throw ValidationException.of(ErrorCode.ITERATION_KEY_INVALID);
        }
    }

    /**
     * 从字符串创建 IterationKey
     */
    public static IterationKey of(String value) {
        return new IterationKey(value);
    }
}
