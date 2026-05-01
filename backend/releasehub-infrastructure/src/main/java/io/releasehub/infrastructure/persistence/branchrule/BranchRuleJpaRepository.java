package io.releasehub.infrastructure.persistence.branchrule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * BranchRule JPA Repository
 */
public interface BranchRuleJpaRepository extends JpaRepository<BranchRuleJpaEntity, String> {
    org.springframework.data.domain.Page<BranchRuleJpaEntity> findByNameContainingIgnoreCase(String name, org.springframework.data.domain.Pageable pageable);
    List<BranchRuleJpaEntity> findByEnabledTrue();
}
