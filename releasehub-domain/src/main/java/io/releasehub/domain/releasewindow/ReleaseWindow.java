package io.releasehub.domain.releasewindow;

import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.base.BaseEntity;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ReleaseWindow extends BaseEntity<ReleaseWindowId> {
    private final String windowKey;
    private final String name;
    private ReleaseWindowStatus status;

    private Instant startAt;
    private Instant endAt;
    private boolean frozen;
    private Instant publishedAt;

    private ReleaseWindow(ReleaseWindowId id, String windowKey, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen, Instant publishedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.windowKey = windowKey;
        this.name = name;
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
        this.frozen = frozen;
        this.publishedAt = publishedAt;
    }

    public static ReleaseWindow createDraft(String windowKey, String name, Instant now) {
        validateName(name);
        validateKey(windowKey);
        return new ReleaseWindow(
                ReleaseWindowId.newId(),
                windowKey,
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

    private static void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw ValidationException.rwKeyRequired();
        }
        if (key.length() > 64) {
            throw ValidationException.rwKeyTooLong(64);
        }
    }

    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw ValidationException.rwNameRequired();
        }
        if (name.length() > 128) {
            throw ValidationException.rwNameTooLong(128);
        }
    }

    public static ReleaseWindow rehydrate(ReleaseWindowId id, String windowKey, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen, Instant publishedAt) {
        return new ReleaseWindow(id, windowKey, name, status, createdAt, updatedAt, startAt, endAt, frozen, publishedAt);
    }

    public void configureWindow(Instant startAt, Instant endAt, Instant now) {
        if (this.frozen) {
            throw BusinessException.rwAlreadyFrozen();
        }
        if (startAt == null || endAt == null) {
            throw BusinessException.rwTimeRequired();
        }
        if (!startAt.isBefore(endAt)) {
            throw BusinessException.rwInvalidTimeRange();
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
            throw BusinessException.rwInvalidState(this.status);
        }
        if (this.startAt == null || this.endAt == null) {
            throw BusinessException.rwNotConfigured();
        }
        
        this.status = ReleaseWindowStatus.PUBLISHED;
        this.publishedAt = now;
        touch(now);
    }


    public void release(Instant now) {
        if (this.status != ReleaseWindowStatus.PUBLISHED) {
            throw BusinessException.rwInvalidState(this.status);
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
            throw BusinessException.rwInvalidState(this.status);
        }
        this.status = ReleaseWindowStatus.CLOSED;
        touch(now);
    }

}
