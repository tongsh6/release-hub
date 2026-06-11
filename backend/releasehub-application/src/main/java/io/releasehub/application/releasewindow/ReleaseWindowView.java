package io.releasehub.application.releasewindow;

import io.releasehub.domain.releasewindow.ReleaseWindow;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Setter
@Getter
public class ReleaseWindowView {
    private String id;
    private String windowKey;
    private String name;
    private String description;
    private Instant plannedReleaseAt;
    private String groupCode;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean frozen;
    private Instant publishedAt;
    private int parallelActiveWindowCount;
    private List<String> parallelActiveWindowKeys = List.of();

    public ReleaseWindowView() {
    }

    public ReleaseWindowView(String id, String windowKey, String name, String description, Instant plannedReleaseAt, String groupCode, String status, Instant createdAt, Instant updatedAt, boolean frozen, Instant publishedAt) {
        this.id = id;
        this.windowKey = windowKey;
        this.name = name;
        this.description = description;
        this.plannedReleaseAt = plannedReleaseAt;
        this.groupCode = groupCode;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.frozen = frozen;
        this.publishedAt = publishedAt;
    }

    public static ReleaseWindowView from(ReleaseWindow rw) {
        return new ReleaseWindowView(
                rw.getId().value(),
                rw.getWindowKey(),
                rw.getName(),
                rw.getDescription(),
                rw.getPlannedReleaseAt(),
                rw.getGroupCode(),
                rw.getStatus().name(),
                rw.getCreatedAt(),
                rw.getUpdatedAt(),
                rw.isFrozen(),
                rw.getPublishedAt()
        );
    }

    public List<String> getParallelActiveWindowKeys() {
        return List.copyOf(parallelActiveWindowKeys);
    }

    public void setParallelActiveWindowKeys(List<String> parallelActiveWindowKeys) {
        this.parallelActiveWindowKeys = parallelActiveWindowKeys == null ? List.of() : List.copyOf(parallelActiveWindowKeys);
    }
}
