package io.releasehub.infrastructure.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeRepositoryJpaRepository extends JpaRepository<CodeRepositoryJpaEntity, String> {
    void deleteById(String id);
}
