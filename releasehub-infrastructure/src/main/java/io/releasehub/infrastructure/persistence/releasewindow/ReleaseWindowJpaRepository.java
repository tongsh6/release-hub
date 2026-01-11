package io.releasehub.infrastructure.persistence.releasewindow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author tongshuanglong
 */
@Repository
public interface ReleaseWindowJpaRepository extends JpaRepository<ReleaseWindowJpaEntity, String> {
}
