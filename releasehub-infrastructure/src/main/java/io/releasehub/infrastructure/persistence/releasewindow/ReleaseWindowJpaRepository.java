package io.releasehub.infrastructure.persistence.releasewindow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author tongshuanglong
 */
@Repository
public interface ReleaseWindowJpaRepository extends JpaRepository<ReleaseWindowJpaEntity, String> {
    Page<ReleaseWindowJpaEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
