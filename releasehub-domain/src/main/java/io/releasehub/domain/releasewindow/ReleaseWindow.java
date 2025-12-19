package io.releasehub.domain.releasewindow;

import io.releasehub.common.exception.BizException;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ReleaseWindow {
    // Getters
    private final ReleaseWindowId id;
    private final String name;
    private final Instant createdAt;
    private ReleaseWindowStatus status;
    private Instant updatedAt;

    private Instant startAt;
    private Instant endAt;
    private boolean frozen;
    private Instant publishedAt;

    // Private constructor for factory/rehydration
    private ReleaseWindow(ReleaseWindowId id, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen, Instant publishedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    // Rehydration method
    public static ReleaseWindow rehydrate(ReleaseWindowId id, String name, ReleaseWindowStatus status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen, Instant publishedAt) {
        return new ReleaseWindow(id, name, status, createdAt, updatedAt, startAt, endAt, frozen, publishedAt);
    }

    public void configureWindow(Instant startAt, Instant endAt, Instant now) {
        if (this.frozen) {
            // 中文注释：冻结后不可配置
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
        this.updatedAt = now;
    }

    public void freeze(Instant now) {
        // 中文注释：冻结必须在 PUBLISHED 之后？用户需求是“冻结后不可配置”，通常是在发布前或发布后冻结。
        // 根据常规流程，DRAFT -> Configure -> Publish -> Freeze -> Release -> Close
        // 或者 DRAFT -> Configure -> Freeze -> Publish -> Release
        // 用户需求： “创建→配置→冻结→发布”
        // 所以 Freeze 可以在 DRAFT 状态下进行，只要配置了时间。
        
        if (this.startAt == null || this.endAt == null) {
             // 隐式规则：未配置不可冻结？用户没明确说，但冻结意味着窗口确定了。
             // 暂不强制，除非用户说“未配置不可冻结”。
             // 用户只说了“未配置不可发布”。
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

    public void publish(Instant now) {
        if (this.status != ReleaseWindowStatus.DRAFT) {
            // 中文注释：重复发布或状态不对
             throw new BizException("RW_INVALID_STATE", "Cannot publish from state: " + this.status);
        }
        if (this.startAt == null || this.endAt == null) {
             // 中文注释：未配置不可发布
             throw new BizException("RW_NOT_CONFIGURED", "ReleaseWindow must be configured before publishing");
        }
        
        this.status = ReleaseWindowStatus.PUBLISHED;
        this.publishedAt = now;
        this.updatedAt = now;
    }


    public void release(Instant now) {
        if (this.status != ReleaseWindowStatus.PUBLISHED) {
            throw new BizException("RW_INVALID_STATE", "Cannot release from state: " + this.status);
        }
        if (!this.frozen) {
            // Implicit rule: must be frozen before release? 
            // Usually yes.
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

}
