package io.releasehub.domain.run;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;

/**
 * 运行项标识符
 * <p>
 * 格式：{windowKey}::{repoId}::{iterationKey}
 */
public record RunItemId(String value) implements EntityId {

    private static final String SEPARATOR = "::";

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
     * 根据组成部分生成 RunItemId
     */
    public static RunItemId generate(String windowKey, RepoId repoId, IterationKey iterationKey) {
        return new RunItemId(windowKey + SEPARATOR + repoId.value() + SEPARATOR + iterationKey.value());
    }
}
