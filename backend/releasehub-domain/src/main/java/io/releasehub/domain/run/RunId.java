package io.releasehub.domain.run;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;

import java.time.Instant;
import java.util.UUID;

/**
 * 运行记录标识符
 * <p>
 * 新记录使用独立 UUID；历史字符串标识继续可读取。
 */
public record RunId(String value) implements EntityId {

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
     * 为每次运行分配独立标识，类型与时间仍保存在 Run 字段中。
     */
    public static RunId generate(RunType runType, Instant timestamp) {
        java.util.Objects.requireNonNull(runType);
        java.util.Objects.requireNonNull(timestamp);
        return new RunId(UUID.randomUUID().toString());
    }
}
