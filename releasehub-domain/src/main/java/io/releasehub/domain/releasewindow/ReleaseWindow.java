package io.releasehub.domain.releasewindow;

import io.releasehub.common.exception.BizException;

import java.time.Instant;

public class ReleaseWindow {
    private final ReleaseWindowId id;
    private final String name;
    private ReleaseWindowStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    
    private Instant startAt;
    private Instant endAt;
    private boolean frozen;

    // Private constructor for factory/rehydration
    private ReleaseWindow(ReleaseWindowId id, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startAt = startAt;
        this.endAt = endAt;
        this.frozen = frozen;
    }

    public static ReleaseWindow createDraft(String name, Instant now) {
        validateName(name);
        return new ReleaseWindow(
                ReleaseWindowId.newId(),
                name,
                ReleaseWindowStatus.DRAFT,
                now,
                now,
                null,
                null,
                false
        );
    }

    // Rehydration method
    public static ReleaseWindow rehydrate(ReleaseWindowId id, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen) {
        return new ReleaseWindow(id, name, status, createdAt, updatedAt, startAt, endAt, frozen);
    }

    public void configureWindow(Instant startAt, Instant endAt, Instant now) {
        if (startAt == null || endAt == null) {
            throw new BizException("RW_INVALID_WINDOW", "StartAt and EndAt must not be null");
        }
        if (!startAt.isBefore(endAt)) {
            throw new BizException("RW_INVALID_WINDOW", "StartAt must be strictly before EndAt");
        }
        this.startAt = startAt;
        this.endAt = endAt;
        this.updatedAt = now;
    }

    public void freeze(Instant now) {
        if (this.status != ReleaseWindowStatus.SUBMITTED) {
            throw new BizException("RW_INVALID_STATE", "Cannot freeze from state: " + this.status);
        }
        if (this.frozen) {
            return;
        }
        this.frozen = true;
        this.updatedAt = now;
    }

    public void unfreeze(Instant now) {
        if (!this.frozen) {
            return;
        }
        this.frozen = false;
        this.updatedAt = now;
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
        if (this.frozen) {
            throw new BizException("RW_FROZEN", "Cannot release a frozen window");
        }
        if (this.startAt != null && this.endAt != null) {
            if (now.isBefore(this.startAt) || now.isAfter(this.endAt)) {
                throw new BizException("RW_OUT_OF_WINDOW", "Current time is outside the release window");
            }
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
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public boolean isFrozen() { return frozen; }
}
