package io.releasehub.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReleaseWindowJpaRepository extends JpaRepository<ReleaseWindowJpaEntity, String> {
}
