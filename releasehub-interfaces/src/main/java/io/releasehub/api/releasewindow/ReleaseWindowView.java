package io.releasehub.api.releasewindow;

import io.releasehub.releasewindow.ReleaseWindow;

import java.time.Instant;

public class ReleaseWindowView {
    private String id;
    private String name;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public ReleaseWindowView() {
    }

    public ReleaseWindowView(String id, String name, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ReleaseWindowView from(ReleaseWindow rw) {
        return new ReleaseWindowView(
                rw.getId().value(),
                rw.getName(),
                rw.getStatus().name(),
                rw.getCreatedAt(),
                rw.getUpdatedAt()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
