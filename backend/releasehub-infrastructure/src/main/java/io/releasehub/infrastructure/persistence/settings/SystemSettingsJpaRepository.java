package io.releasehub.infrastructure.persistence.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemSettingsJpaRepository extends JpaRepository<SystemSettingsJpaEntity, String> {
}
