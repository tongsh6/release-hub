package io.releasehub.releasewindow;

import java.util.Objects;
import java.util.UUID;

public record ReleaseWindowId(String value) {
    public ReleaseWindowId {
        Objects.requireNonNull(value, "ReleaseWindowId value must not be null");
    }

    public static ReleaseWindowId newId() {
        return new ReleaseWindowId(UUID.randomUUID().toString());
    }
}
