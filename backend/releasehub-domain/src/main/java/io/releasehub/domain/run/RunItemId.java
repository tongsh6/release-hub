package io.releasehub.domain.run;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import java.util.UUID;

/**
 * 运行项标识符
 * <p>
 * 新执行项使用独立 UUID，业务范围不再充当数据库主键；旧标识继续可读取。
 */
public record RunItemId(String value) implements EntityId {

    public RunItemId {
        if (value == null || value.isBlank()) {
            throw ValidationException.of(ErrorCode.RUN_ITEM_ID_INVALID);
        }
    }

    /**
     * 从字符串创建 RunItemId
     */
    public static RunItemId of(String value) {
        return new RunItemId(value);
    }

    /**
     * 为每个执行项分配独立标识。
     */
    public static RunItemId generate(String windowKey, RepoId repoId, IterationKey iterationKey) {
        java.util.Objects.requireNonNull(repoId);
        java.util.Objects.requireNonNull(iterationKey);
        return new RunItemId(UUID.randomUUID().toString());
    }
}
