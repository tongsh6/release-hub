package io.releasehub.infrastructure.persistence.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectJpaRepository extends JpaRepository<ProjectJpaEntity, String> {
    Page<ProjectJpaEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<ProjectJpaEntity> findByStatus(String status, Pageable pageable);
    Page<ProjectJpaEntity> findByNameContainingIgnoreCaseAndStatus(String name, String status, Pageable pageable);
}
