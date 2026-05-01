package io.releasehub.domain.window;

import io.releasehub.domain.base.BaseEntity;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindowId;

import java.time.Instant;

public class WindowIteration extends BaseEntity<WindowIterationId> {
    private final ReleaseWindowId windowId;
    private final IterationKey iterationKey;
    private final Instant attachAt;
    private final String releaseBranch;
    private final Boolean branchCreated;
    private final Instant lastMergeAt;

    private WindowIteration(WindowIterationId id, ReleaseWindowId windowId, IterationKey iterationKey,
            Instant attachAt, String releaseBranch, Boolean branchCreated, Instant lastMergeAt,
            Instant createdAt, Instant updatedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.windowId = windowId;
        this.iterationKey = iterationKey;
        this.attachAt = attachAt;
        this.releaseBranch = releaseBranch;
        this.branchCreated = branchCreated;
        this.lastMergeAt = lastMergeAt;
    }

    private WindowIteration(WindowIterationId id, ReleaseWindowId windowId, IterationKey iterationKey,
            Instant attachAt, Instant now) {
        super(id, now);
        this.windowId = windowId;
        this.iterationKey = iterationKey;
        this.attachAt = attachAt;
        this.releaseBranch = null;
        this.branchCreated = false;
        this.lastMergeAt = null;
    }

    public static WindowIteration attach(ReleaseWindowId windowId, IterationKey iterationKey, Instant attachAt, Instant now) {
        WindowIterationId id = WindowIterationId.generate(windowId, iterationKey);
        return new WindowIteration(id, windowId, iterationKey, attachAt, now);
    }

    public static WindowIteration rehydrate(WindowIterationId id, ReleaseWindowId windowId, IterationKey iterationKey,
            Instant attachAt, String releaseBranch, Boolean branchCreated, Instant lastMergeAt,
            Instant createdAt, Instant updatedAt) {
        return new WindowIteration(id, windowId, iterationKey, attachAt, releaseBranch, branchCreated, lastMergeAt,
                createdAt, updatedAt);
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

    public String getReleaseBranch() {
        return releaseBranch;
    }

    public Boolean getBranchCreated() {
        return branchCreated;
    }

    public Instant getLastMergeAt() {
        return lastMergeAt;
    }
}
