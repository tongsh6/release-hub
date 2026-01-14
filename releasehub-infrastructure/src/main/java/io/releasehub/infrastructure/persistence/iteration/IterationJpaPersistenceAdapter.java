package io.releasehub.infrastructure.persistence.iteration;

import io.releasehub.application.iteration.IterationPort;
import io.releasehub.common.paging.PageResult;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
                iteration.getName(),
                iteration.getDescription(),
                iteration.getExpectedReleaseAt(),
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
                    return Iteration.rehydrate(IterationKey.of(e.getKey()), e.getName(), e.getDescription(), e.getExpectedReleaseAt(), repoIds, e.getCreatedAt(), e.getUpdatedAt());
                });
    }

    @Override
    public List<Iteration> findAll() {
        return iterationRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<Iteration> findPaged(String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        PageRequest pageable = PageRequest.of(pageIndex, size);
        Page<IterationJpaEntity> result;
        if (keyword == null || keyword.isBlank()) {
            result = iterationRepository.findAll(pageable);
        } else {
            String k = keyword.trim();
            result = iterationRepository.findByKeyContainingIgnoreCaseOrNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(k, k, k, pageable);
        }
        List<Iteration> items = result.getContent().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        return new PageResult<>(items, result.getTotalElements());
    }

    @Override
    public void deleteByKey(IterationKey key) {
        iterationRepoRepository.deleteByIdIterationKey(key.value());
        iterationRepository.deleteById(key.value());
    }

    private Iteration toDomain(IterationJpaEntity entity) {
        List<IterationRepoJpaEntity> repos = iterationRepoRepository.findByIdIterationKey(entity.getKey());
        Set<RepoId> repoIds = repos.stream()
                .map(r -> RepoId.of(r.getId().getRepoId()))
                .collect(Collectors.toCollection(HashSet::new));
        return Iteration.rehydrate(
                IterationKey.of(entity.getKey()),
                entity.getName(),
                entity.getDescription(),
                entity.getExpectedReleaseAt(),
                repoIds,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
