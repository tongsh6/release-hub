package io.releasehub.domain.repo;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;

import java.util.UUID;

/**
 * 代码仓库标识符
 */
public record RepoId(String value) implements EntityId {

    public RepoId {
        if (value == null || value.isBlank()) {
            throw ValidationException.of(ErrorCode.REPO_ID_INVALID);
        }
    }

    /**
     * 生成新的 RepoId
     */
    public static RepoId newId() {
        return new RepoId(UUID.randomUUID().toString());
    }

    /**
     * 从字符串创建 RepoId
     */
    public static RepoId of(String value) {
        return new RepoId(value);
    }
}
