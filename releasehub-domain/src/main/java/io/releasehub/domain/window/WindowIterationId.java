package io.releasehub.domain.window;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;

/**
 * 窗口迭代关联标识符
 * <p>
 * 格式：{windowId}::{iterationKey}
 */
public record WindowIterationId(String value) implements EntityId {

    private static final String SEPARATOR = "::";

    public WindowIterationId {
        if (value == null || value.isBlank()) {
            throw ValidationException.of(ErrorCode.WINDOW_ITERATION_ID_INVALID);
        }
    }

    /**
     * 从字符串创建 WindowIterationId
     */
    public static WindowIterationId of(String value) {
        return new WindowIterationId(value);
    }

    /**
     * 根据窗口ID和迭代Key生成 WindowIterationId
     */
    public static WindowIterationId generate(ReleaseWindowId windowId, IterationKey iterationKey) {
        return new WindowIterationId(windowId.value() + SEPARATOR + iterationKey.value());
    }
}
