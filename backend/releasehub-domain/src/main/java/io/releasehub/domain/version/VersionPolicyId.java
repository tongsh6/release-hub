package io.releasehub.domain.version;

import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.EntityId;

import java.util.UUID;

/**
 * 版本策略标识符
 */
public record VersionPolicyId(String value) implements EntityId {

    public VersionPolicyId {
        if (value == null || value.isBlank()) {
            throw ValidationException.of(ErrorCode.VERSION_POLICY_ID_INVALID);
        }
    }

    /**
     * 生成新的 VersionPolicyId
     */
    public static VersionPolicyId newId() {
        return new VersionPolicyId(UUID.randomUUID().toString());
    }

    /**
     * 从字符串创建 VersionPolicyId
     */
    public static VersionPolicyId of(String value) {
        return new VersionPolicyId(value);
    }
}
