package io.releasehub.infrastructure.persistence.releasegovernance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReleaseCandidateSignoffJpaRepository extends JpaRepository<ReleaseCandidateSignoffJpaEntity, String> {
    Optional<ReleaseCandidateSignoffJpaEntity> findTopByCandidateIdOrderByCreatedAtDesc(String candidateId);
}
