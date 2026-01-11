package io.releasehub.infrastructure.persistence.iteration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IterationJpaRepository extends JpaRepository<IterationJpaEntity, String> {
}

