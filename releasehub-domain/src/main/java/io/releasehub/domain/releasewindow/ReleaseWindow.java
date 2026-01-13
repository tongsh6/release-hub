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
    private final String description;
    private final Instant plannedReleaseAt;
    private ReleaseWindowStatus status;

    private boolean frozen;
    private Instant publishedAt;

    private ReleaseWindow(ReleaseWindowId id, String windowKey, String name, String description, Instant plannedReleaseAt, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, boolean frozen, Instant publishedAt) {
        super(id, createdAt, updatedAt, 0L);
        this.windowKey = windowKey;
        this.name = name;
        this.description = description;
        this.plannedReleaseAt = plannedReleaseAt;
        this.status = status;
        this.frozen = frozen;
        this.publishedAt = publishedAt;
    }

    public static ReleaseWindow createDraft(String windowKey, String name, String description, Instant plannedReleaseAt, Instant now) {
        validateName(name);
        validateKey(windowKey);
        return new ReleaseWindow(
                ReleaseWindowId.newId(),
                windowKey,
                name,
                description,
                plannedReleaseAt,
                ReleaseWindowStatus.DRAFT,
                now,
                now,
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

    public static ReleaseWindow rehydrate(ReleaseWindowId id, String windowKey, String name, String description, Instant plannedReleaseAt, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, boolean frozen, Instant publishedAt) {
        return new ReleaseWindow(id, windowKey, name, description, plannedReleaseAt, status, createdAt, updatedAt, frozen, publishedAt);
    }

    public void freeze(Instant now) {
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
        
        this.status = ReleaseWindowStatus.PUBLISHED;
        this.publishedAt = now;
        touch(now);
    }

    public void close(Instant now) {
        if (this.status == ReleaseWindowStatus.CLOSED) {
            return; // Idempotent
        }
        if (this.status != ReleaseWindowStatus.PUBLISHED) {
            throw BusinessException.rwInvalidState(this.status);
        }
        this.status = ReleaseWindowStatus.CLOSED;
        touch(now);
    }

}
