package io.releasehub.application.iteration;

import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.NotFoundException;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.window.WindowIteration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IterationAppService {
    private final IterationPort iterationPort;
    private final ReleaseWindowPort releaseWindowPort;
    private final WindowIterationPort windowIterationPort;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public Iteration create(String iterationKey, String description, Set<String> repoIds) {
        Set<String> safeRepoIds = repoIds == null ? java.util.Set.of() : repoIds;
        Set<RepoId> repos = safeRepoIds.stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        Iteration it = Iteration.create(IterationKey.of(iterationKey), description, repos, Instant.now(clock));
        iterationPort.save(it);
        return it;
    }

    public List<Iteration> list() {
        return iterationPort.findAll();
    }

    @Transactional
    public Iteration update(String key, String description, Set<String> repoIds) {
        Iteration existing = get(key);
        Set<String> safeRepoIds = repoIds == null ? java.util.Set.of() : repoIds;
        Set<RepoId> repos = safeRepoIds.stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        Instant now = Instant.now(clock);
        Iteration updated = Iteration.rehydrate(existing.getId(), description, repos, existing.getCreatedAt(), now);
        iterationPort.save(updated);
        return updated;
    }

    public Iteration get(String key) {
        return iterationPort.findByKey(IterationKey.of(key))
                            .orElseThrow(() -> NotFoundException.iteration(key));
    }

    @Transactional
    public Iteration addRepos(String key, Set<String> repoIds) {
        Iteration existing = get(key);
        Set<RepoId> toAdd = (repoIds == null ? java.util.Set.<String>of() : repoIds).stream().map(RepoId::new).collect(java.util.stream.Collectors.toSet());
        java.util.Set<RepoId> merged = new java.util.LinkedHashSet<>(existing.getRepos());
        merged.addAll(toAdd);
        Instant now = Instant.now(clock);
        Iteration updated = Iteration.rehydrate(existing.getId(), existing.getDescription(), merged, existing.getCreatedAt(), now);
        iterationPort.save(updated);
        return updated;
    }

    @Transactional
    public Iteration removeRepos(String key, Set<String> repoIds) {
        Iteration existing = get(key);
        java.util.Set<String> toRemove = repoIds == null ? java.util.Set.of() : repoIds;
        java.util.Set<RepoId> filtered = existing.getRepos().stream()
                                                 .filter(r -> !toRemove.contains(r.value()))
                                                 .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Instant now = Instant.now(clock);
        Iteration updated = Iteration.rehydrate(existing.getId(), existing.getDescription(), filtered, existing.getCreatedAt(), now);
        iterationPort.save(updated);
        return updated;
    }

    public java.util.Set<String> listRepos(String key) {
        Iteration existing = get(key);
        return existing.getRepos().stream().map(RepoId::value).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Transactional
    public void delete(String key) {
        Iteration existing = get(key);
        List<ReleaseWindow> windows = releaseWindowPort.findAll();
        boolean attached = windows.stream()
                                  .map(w -> windowIterationPort.listByWindow(ReleaseWindowId.of(w.getId().value())))
                                  .flatMap(List::stream)
                                  .map(WindowIteration::getIterationKey)
                                  .anyMatch(k -> k.equals(existing.getId()));
        if (attached) {
            throw BusinessException.iterationAttached(key);
        }
        iterationPort.deleteByKey(existing.getId());
    }
}
