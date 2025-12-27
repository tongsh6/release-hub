package io.releasehub.infrastructure.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeRepositoryJpaRepository extends JpaRepository<CodeRepositoryJpaEntity, String> {
    List<CodeRepositoryJpaEntity> findByProjectId(String projectId);
}
