package io.releasehub.infrastructure.persistence.releasewindow;

import java.util.List;

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
           "(:name IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:groupCodes IS NULL OR r.groupCode IN :groupCodes)")
    Page<ReleaseWindowJpaEntity> findByNameStatusAndGroupCodes(
            @Param("name") String name,
            @Param("status") String status,
            @Param("groupCodes") List<String> groupCodes,
            Pageable pageable);
}
