package io.releasehub.infrastructure.persistence.branchrule;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * BranchRule JPA Repository
 */
public interface BranchRuleJpaRepository extends JpaRepository<BranchRuleJpaEntity, String> {
}
