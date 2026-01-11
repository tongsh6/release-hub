package io.releasehub.domain.iteration;

import io.releasehub.domain.base.BaseEntity;
import io.releasehub.domain.repo.RepoId;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class Iteration extends BaseEntity<IterationKey> {
    private final String description;
    private final Set<RepoId> repos;

    private Iteration(IterationKey key, String description, Set<RepoId> repos, Instant createdAt, Instant updatedAt) {
        super(key, createdAt, updatedAt, 0L);
        this.description = description;
        this.repos = new LinkedHashSet<>(Objects.requireNonNull(repos));
    }

    private Iteration(IterationKey key, String description, Set<RepoId> repos, Instant now) {
        super(key, now);
        this.description = description;
        this.repos = new LinkedHashSet<>(Objects.requireNonNull(repos));
    }

    public static Iteration create(IterationKey key, String description, Set<RepoId> repos, Instant now) {
        return new Iteration(key, description, repos, now);
    }

    public static Iteration rehydrate(IterationKey key, String description, Set<RepoId> repos, Instant createdAt, Instant updatedAt) {
        return new Iteration(key, description, repos, createdAt, updatedAt);
    }

    public String getDescription() {
        return description;
    }

    public Set<RepoId> getRepos() {
        return Set.copyOf(repos);
    }
}
