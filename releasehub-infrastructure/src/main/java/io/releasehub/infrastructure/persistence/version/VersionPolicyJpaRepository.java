package io.releasehub.infrastructure.persistence.version;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * VersionPolicy JPA Repository
 */
public interface VersionPolicyJpaRepository extends JpaRepository<VersionPolicyJpaEntity, String> {
}
