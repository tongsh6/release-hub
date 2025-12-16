package io.releasehub.releasewindow;

import io.releasehub.common.exception.BizException;

import java.time.Instant;

public class ReleaseWindow {
    private final ReleaseWindowId id;
    private final String name;
    private ReleaseWindowStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    // Private constructor for factory/rehydration
    private ReleaseWindow(ReleaseWindowId id, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ReleaseWindow createDraft(String name, Instant now) {
        validateName(name);
        return new ReleaseWindow(
                ReleaseWindowId.newId(),
                name,
                ReleaseWindowStatus.DRAFT,
                now,
                now
        );
    }

    // Rehydration method (no logic validation except non-nulls if needed, assuming DB state is valid)
    public static ReleaseWindow rehydrate(ReleaseWindowId id, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt) {
        return new ReleaseWindow(id, name, status, createdAt, updatedAt);
    }

    public void submit(Instant now) {
        if (this.status != ReleaseWindowStatus.DRAFT) {
            throw new BizException("RW_INVALID_STATE", "Cannot submit from state: " + this.status);
        }
        this.status = ReleaseWindowStatus.SUBMITTED;
        this.updatedAt = now;
    }

    public void release(Instant now) {
        if (this.status != ReleaseWindowStatus.SUBMITTED) {
            throw new BizException("RW_INVALID_STATE", "Cannot release from state: " + this.status);
        }
        this.status = ReleaseWindowStatus.RELEASED;
        this.updatedAt = now;
    }

    public void close(Instant now) {
        if (this.status == ReleaseWindowStatus.CLOSED) {
            return; // Idempotent
        }
        if (this.status != ReleaseWindowStatus.RELEASED) {
            throw new BizException("RW_INVALID_STATE", "Cannot close from state: " + this.status);
        }
        this.status = ReleaseWindowStatus.CLOSED;
        this.updatedAt = now;
    }

    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BizException("RW_NAME_REQUIRED", "ReleaseWindow name is required");
        }
        if (name.length() > 128) {
            throw new BizException("RW_NAME_TOO_LONG", "ReleaseWindow name is too long (max 128)");
        }
    }

    // Getters
    public ReleaseWindowId getId() { return id; }
    public String getName() { return name; }
    public ReleaseWindowStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
