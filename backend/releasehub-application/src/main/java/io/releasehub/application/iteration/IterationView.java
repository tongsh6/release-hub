package io.releasehub.application.iteration;

import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.repo.RepoId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class IterationView {
    private String key;
    private String name;
    private String description;
    private LocalDate expectedReleaseAt;
    private String groupCode;
    private Set<String> repoIds;
    private boolean attachedToWindow;
    private Set<String> attachedWindowIds = Set.of();
    private Instant createdAt;
    private Instant updatedAt;

    public static IterationView fromDomain(Iteration it) {
        IterationView v = new IterationView();
        v.key = it.getId().value();
        v.name = it.getName();
        v.description = it.getDescription();
        v.expectedReleaseAt = it.getExpectedReleaseAt();
        v.groupCode = it.getGroupCode();
        v.repoIds = it.getRepos().stream().map(RepoId::value).collect(Collectors.toUnmodifiableSet());
        v.createdAt = it.getCreatedAt();
        v.updatedAt = it.getUpdatedAt();
        return v;
    }

    public static IterationView fromDomain(Iteration it, Collection<String> attachedWindowIds) {
        IterationView v = fromDomain(it);
        v.attachedWindowIds = attachedWindowIds == null ? Set.of() : Set.copyOf(attachedWindowIds);
        v.attachedToWindow = !v.attachedWindowIds.isEmpty();
        return v;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDate getExpectedReleaseAt() { return expectedReleaseAt; }
    public String getGroupCode() { return groupCode; }
    public Set<String> getRepoIds() { return Set.copyOf(repoIds); }
    public boolean isAttachedToWindow() { return attachedToWindow; }
    public Set<String> getAttachedWindowIds() { return Set.copyOf(attachedWindowIds); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
