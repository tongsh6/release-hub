package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.domain.releasewindow.ReleaseWindow;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
public class ReleaseWindowView {
    private String id;
    private String name;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant startAt;
    private Instant endAt;
    private boolean frozen;

    public ReleaseWindowView() {
    }

    public ReleaseWindowView(String id, String name, String status, Instant createdAt, Instant updatedAt, Instant startAt, Instant endAt, boolean frozen) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startAt = startAt;
        this.endAt = endAt;
        this.frozen = frozen;
    }

    public static ReleaseWindowView from(ReleaseWindow rw) {
        return new ReleaseWindowView(
                rw.getId().value(),
                rw.getName(),
                rw.getStatus().name(),
                rw.getCreatedAt(),
                rw.getUpdatedAt(),
                rw.getStartAt(),
                rw.getEndAt(),
                rw.isFrozen()
        );
    }

}
