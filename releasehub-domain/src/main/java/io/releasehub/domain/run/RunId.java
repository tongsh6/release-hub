package io.releasehub.domain.run;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;

import java.time.Instant;

/**
 * 运行记录标识符
 * <p>
 * 格式：{runType}::{timestamp}
 */
public record RunId(String value) implements EntityId {

    private static final String SEPARATOR = "::";

    public RunId {
        if (value == null || value.isBlank()) {
            throw ValidationException.of(ErrorCode.RUN_ID_INVALID);
        }
    }

    /**
     * 从字符串创建 RunId
     */
    public static RunId of(String value) {
        return new RunId(value);
    }

    /**
     * 根据运行类型和时间生成 RunId
     */
    public static RunId generate(RunType runType, Instant timestamp) {
        return new RunId(runType.name() + SEPARATOR + timestamp.toEpochMilli());
    }
}
