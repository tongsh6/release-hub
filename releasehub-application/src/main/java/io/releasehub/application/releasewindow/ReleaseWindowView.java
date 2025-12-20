package io.releasehub.application.releasewindow;

import io.releasehub.domain.releasewindow.ReleaseWindow;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
public class ReleaseWindowView {
    private String id;
    private String windowKey;
    private String name;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant startAt;
    private Instant endAt;
    private boolean frozen;
    private Instant publishedAt;

    public ReleaseWindowView() {
    }

    public ReleaseWindowView(String id, String windowKey, String name, String status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen, Instant publishedAt) {
        this.id = id;
        this.windowKey = windowKey;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startAt = startAt;
        this.endAt = endAt;
        this.frozen = frozen;
        this.publishedAt = publishedAt;
    }

    public static ReleaseWindowView from(ReleaseWindow rw) {
        return new ReleaseWindowView(
                rw.getId().value(),
                rw.getWindowKey(),
                rw.getName(),
                rw.getStatus().name(),
                rw.getCreatedAt(),
                rw.getUpdatedAt(),
                rw.getStartAt(),
                rw.getEndAt(),
                rw.isFrozen(),
                rw.getPublishedAt()
        );
    }
}
