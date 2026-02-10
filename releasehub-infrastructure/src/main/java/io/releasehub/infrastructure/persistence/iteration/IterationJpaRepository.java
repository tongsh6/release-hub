package io.releasehub.infrastructure.persistence.iteration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IterationJpaRepository extends JpaRepository<IterationJpaEntity, String> {
    Page<IterationJpaEntity> findByKeyContainingIgnoreCaseOrNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String key,
            String name,
            String description,
            Pageable pageable
    );
}
