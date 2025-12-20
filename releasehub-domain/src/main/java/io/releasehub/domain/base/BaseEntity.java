package io.releasehub.domain.base;

import java.time.Instant;
import java.util.Objects;

public abstract class BaseEntity<ID> implements Entity<ID> {
    private final ID id;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    protected BaseEntity(ID id, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
        this.version = 0L;
    }

    protected BaseEntity(ID id, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    @Override
    public ID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now);
        this.version = this.version + 1;
    }

    public boolean sameIdentityAs(Entity<ID> other) {
        return other != null && Objects.equals(this.id, other.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity<?> b)) return false;
        return Objects.equals(id, b.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
