package io.releasehub.infrastructure.persistence.branchrule;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * BranchRule JPA Repository
 */
public interface BranchRuleJpaRepository extends JpaRepository<BranchRuleJpaEntity, String> {
    Page<BranchRuleJpaEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
