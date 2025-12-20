package io.releasehub.domain.releasewindow;

import io.releasehub.common.exception.BizException;
import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ReleaseWindow extends BaseEntity<ReleaseWindowId> {
    private final String name;
    private ReleaseWindowStatus status;

    private Instant startAt;
    private Instant endAt;
    private boolean frozen;
    private Instant publishedAt;

    private ReleaseWindow(ReleaseWindowId id, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen, Instant publishedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.name = name;
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
        this.frozen = frozen;
        this.publishedAt = publishedAt;
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
                false,
                null
        );
    }

    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BizException("RW_NAME_REQUIRED", "ReleaseWindow name is required");
        }
        if (name.length() > 128) {
            throw new BizException("RW_NAME_TOO_LONG", "ReleaseWindow name is too long (max 128)");
        }
    }

    public static ReleaseWindow rehydrate(ReleaseWindowId id, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen, Instant publishedAt) {
        return new ReleaseWindow(id, name, status, createdAt, updatedAt, startAt, endAt, frozen, publishedAt);
    }

    public void configureWindow(Instant startAt, Instant endAt, Instant now) {
        if (this.frozen) {
            throw new BizException("RW_ALREADY_FROZEN", "Cannot configure frozen ReleaseWindow");
        }
        if (startAt == null || endAt == null) {
            throw new BizException("RW_INVALID_WINDOW", "StartAt and EndAt must not be null");
        }
        if (!startAt.isBefore(endAt)) {
            throw new BizException("RW_INVALID_WINDOW", "StartAt must be strictly before EndAt");
        }
        this.startAt = startAt;
        this.endAt = endAt;
        touch(now);
    }

    public void freeze(Instant now) {
        if (this.startAt == null || this.endAt == null) {
        }
        if (this.frozen) {
            return;
        }
        this.frozen = true;
        touch(now);
    }

    public void unfreeze(Instant now) {
        if (!this.frozen) {
            return;
        }
        this.frozen = false;
        touch(now);
    }

    public void publish(Instant now) {
        if (this.status != ReleaseWindowStatus.DRAFT) {
             throw new BizException("RW_INVALID_STATE", "Cannot publish from state: " + this.status);
        }
        if (this.startAt == null || this.endAt == null) {
             throw new BizException("RW_NOT_CONFIGURED", "ReleaseWindow must be configured before publishing");
        }
        
        this.status = ReleaseWindowStatus.PUBLISHED;
        this.publishedAt = now;
        touch(now);
    }


    public void release(Instant now) {
        if (this.status != ReleaseWindowStatus.PUBLISHED) {
            throw new BizException("RW_INVALID_STATE", "Cannot release from state: " + this.status);
        }
        if (!this.frozen) {
        }
        this.status = ReleaseWindowStatus.RELEASED;
        touch(now);
    }

    public void close(Instant now) {
        if (this.status == ReleaseWindowStatus.CLOSED) {
            return; // Idempotent
        }
        if (this.status != ReleaseWindowStatus.RELEASED) {
            throw new BizException("RW_INVALID_STATE", "Cannot close from state: " + this.status);
        }
        this.status = ReleaseWindowStatus.CLOSED;
        touch(now);
    }

}
