package io.releasehub.infrastructure.persistence.iteration;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@Primary
@RequiredArgsConstructor
public class IterationJpaPersistenceAdapter implements IterationPort {

    private final IterationJpaRepository iterationRepository;
    private final IterationRepoJpaRepository iterationRepoRepository;

    @Override
    public void save(Iteration iteration) {
        IterationJpaEntity entity = new IterationJpaEntity(
                iteration.getId().value(),
                iteration.getDescription(),
                iteration.getCreatedAt(),
                iteration.getUpdatedAt()
        );
        iterationRepository.save(entity);
        Set<String> newRepos = iteration.getRepos().stream().map(RepoId::value).collect(Collectors.toSet());
        List<IterationRepoJpaEntity> existing = iterationRepoRepository.findByIdIterationKey(iteration.getId().value());
        Set<String> oldRepos = existing.stream().map(e -> e.getId().getRepoId()).collect(Collectors.toSet());
        for (String toDel : oldRepos) {
            if (!newRepos.contains(toDel)) {
                iterationRepoRepository.deleteByIdIterationKeyAndIdRepoId(iteration.getId().value(), toDel);
            }
        }
        for (String toAdd : newRepos) {
            if (!oldRepos.contains(toAdd)) {
                iterationRepoRepository.save(new IterationRepoJpaEntity(new IterationRepoId(iteration.getId().value(), toAdd)));
            }
        }
    }

    @Override
    public Optional<Iteration> findByKey(IterationKey key) {
        return iterationRepository.findById(key.value())
                .map(e -> {
                    List<IterationRepoJpaEntity> repos = iterationRepoRepository.findByIdIterationKey(e.getKey());
                    Set<RepoId> repoIds = repos.stream().map(r -> RepoId.of(r.getId().getRepoId())).collect(Collectors.toCollection(HashSet::new));
                    return Iteration.rehydrate(IterationKey.of(e.getKey()), e.getDescription(), repoIds, e.getCreatedAt(), e.getUpdatedAt());
                });
    }

    @Override
    public List<Iteration> findAll() {
        return iterationRepository.findAll().stream()
                .map(e -> {
                    List<IterationRepoJpaEntity> repos = iterationRepoRepository.findByIdIterationKey(e.getKey());
                    Set<RepoId> repoIds = repos.stream().map(r -> RepoId.of(r.getId().getRepoId())).collect(Collectors.toCollection(HashSet::new));
                    return Iteration.rehydrate(IterationKey.of(e.getKey()), e.getDescription(), repoIds, e.getCreatedAt(), e.getUpdatedAt());
                })
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByKey(IterationKey key) {
        iterationRepoRepository.deleteByIdIterationKey(key.value());
        iterationRepository.deleteById(key.value());
    }
}

