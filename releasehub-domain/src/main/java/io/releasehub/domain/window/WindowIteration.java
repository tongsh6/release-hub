package io.releasehub.domain.window;

import io.releasehub.domain.base.BaseEntity;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;

import java.time.Instant;

public class WindowIteration extends BaseEntity<String> {
    private final ReleaseWindowId windowId;
    private final IterationKey iterationKey;
    private final Instant attachAt;

    private WindowIteration(String id, ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt, Instant createdAt, Instant updatedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.windowId = windowId;
        this.iterationKey = iterationKey;
        this.attachAt = attachAt;
    }

    private WindowIteration(String id, ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt, Instant now) {
        super(id, now);
        this.windowId = windowId;
        this.iterationKey = iterationKey;
        this.attachAt = attachAt;
    }

    public static WindowIteration attach(ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt, Instant now) {
        String id = windowId.value() + "::" + iterationKey.value();
        return new WindowIteration(id, windowId, iterationKey, attachAt, now);
    }

    public static WindowIteration rehydrate(String id, ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt, Instant createdAt, Instant updatedAt) {
        return new WindowIteration(id, windowId, iterationKey, attachAt, createdAt, updatedAt);
    }

    public ReleaseWindowId getWindowId() {
        return windowId;
    }

    public IterationKey getIterationKey() {
        return iterationKey;
    }

    public Instant getAttachAt() {
        return attachAt;
    }
}
