package io.releasehub.application.iteration;

import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.repo.RepoId;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public class IterationView {
    private String key;
    private String description;
    private Set<String> repoIds;
    private Instant createdAt;
    private Instant updatedAt;

    public static IterationView fromDomain(Iteration it) {
        IterationView v = new IterationView();
        v.key = it.getId().value();
        v.description = it.getDescription();
        v.repoIds = it.getRepos().stream().map(RepoId::value).collect(Collectors.toSet());
        v.createdAt = it.getCreatedAt();
        v.updatedAt = it.getUpdatedAt();
        return v;
    }

    public String getKey() { return key; }
    public String getDescription() { return description; }
    public Set<String> getRepoIds() { return repoIds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

