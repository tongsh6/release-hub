package io.releasehub.domain.version;

import java.util.UUID;

public record VersionPolicyId(String value) {
    public VersionPolicyId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("VersionPolicyId cannot be null or empty");
        }
    }

    public static VersionPolicyId newId() {
        return new VersionPolicyId(UUID.randomUUID().toString());
    }
}
