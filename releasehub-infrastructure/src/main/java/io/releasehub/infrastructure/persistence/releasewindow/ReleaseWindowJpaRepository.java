package io.releasehub.infrastructure.persistence.releasewindow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * @author tongshuanglong
 */
@Repository
public interface ReleaseWindowJpaRepository extends JpaRepository<ReleaseWindowJpaEntity, String> {
    Page<ReleaseWindowJpaEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<ReleaseWindowJpaEntity> findByStatus(String status, Pageable pageable);

    @Query("SELECT r FROM ReleaseWindowJpaEntity r WHERE " +
           "(:name IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:status IS NULL OR r.status = :status)")
    Page<ReleaseWindowJpaEntity> findByNameAndStatus(
            @Param("name") String name,
            @Param("status") String status,
            Pageable pageable);
}
