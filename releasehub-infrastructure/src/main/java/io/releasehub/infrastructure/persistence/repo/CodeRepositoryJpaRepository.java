package io.releasehub.infrastructure.persistence.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeRepositoryJpaRepository extends JpaRepository<CodeRepositoryJpaEntity, String> {
    void deleteById(String id);

    Page<CodeRepositoryJpaEntity> findByNameContainingIgnoreCaseOrCloneUrlContainingIgnoreCase(String name, String cloneUrl, Pageable pageable);
}
