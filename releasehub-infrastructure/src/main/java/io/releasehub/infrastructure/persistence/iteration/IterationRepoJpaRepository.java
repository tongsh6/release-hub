package io.releasehub.infrastructure.persistence.iteration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IterationRepoJpaRepository extends JpaRepository<IterationRepoJpaEntity, IterationRepoId> {
    List<IterationRepoJpaEntity> findByIdIterationKey(String iterationKey);
    void deleteByIdIterationKey(String iterationKey);
    void deleteByIdIterationKeyAndIdRepoId(String iterationKey, String repoId);
}

