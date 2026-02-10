package io.releasehub.domain.iteration;

import io.releasehub.domain.base.BaseEntity;
import io.releasehub.domain.repo.RepoId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class Iteration extends BaseEntity<IterationKey> {
    private final String name;
    private final String description;
    private final LocalDate expectedReleaseAt;
    private final String groupCode;
    private final Set<RepoId> repos;
    private IterationStatus status;

    private Iteration(IterationKey key, String name, String description, LocalDate expectedReleaseAt, 
                     String groupCode, Set<RepoId> repos, IterationStatus status, 
                     Instant createdAt, Instant updatedAt) {
        super(key, createdAt, updatedAt, 0L);
        this.name = name;
        this.description = description;
        this.expectedReleaseAt = expectedReleaseAt;
        this.groupCode = groupCode;
        this.repos = new LinkedHashSet<>(Objects.requireNonNull(repos));
        this.status = status;
    }

    private Iteration(IterationKey key, String name, String description, LocalDate expectedReleaseAt, 
                     String groupCode, Set<RepoId> repos, Instant now) {
        super(key, now);
        this.name = name;
        this.description = description;
        this.expectedReleaseAt = expectedReleaseAt;
        this.groupCode = groupCode;
        this.repos = new LinkedHashSet<>(Objects.requireNonNull(repos));
        this.status = IterationStatus.ACTIVE; // 新建迭代默认为 ACTIVE
    }

    public static Iteration create(IterationKey key, String name, String description, 
                                   LocalDate expectedReleaseAt, String groupCode, 
                                   Set<RepoId> repos, Instant now) {
        return new Iteration(key, name, description, expectedReleaseAt, groupCode, repos, now);
    }

    public static Iteration rehydrate(IterationKey key, String name, String description, 
                                      LocalDate expectedReleaseAt, String groupCode, 
                                      Set<RepoId> repos, IterationStatus status,
                                      Instant createdAt, Instant updatedAt) {
        return new Iteration(key, name, description, expectedReleaseAt, groupCode, repos, 
                            status, createdAt, updatedAt);
    }

    /**
     * 关闭迭代
     */
    public void close(Instant now) {
        if (this.status == IterationStatus.CLOSED) {
            return; // 幂等
        }
        this.status = IterationStatus.CLOSED;
        touch(now);
    }

    /**
     * 检查迭代是否已关闭
     */
    public boolean isClosed() {
        return this.status == IterationStatus.CLOSED;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getExpectedReleaseAt() {
        return expectedReleaseAt;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public Set<RepoId> getRepos() {
        return Set.copyOf(repos);
    }

    public IterationStatus getStatus() {
        return status;
    }
}
