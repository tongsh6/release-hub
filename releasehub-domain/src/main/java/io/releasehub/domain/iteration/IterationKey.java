package io.releasehub.domain.iteration;

import java.util.Objects;

public record IterationKey(String value) {
    public IterationKey {
        Objects.requireNonNull(value);
    }
}
