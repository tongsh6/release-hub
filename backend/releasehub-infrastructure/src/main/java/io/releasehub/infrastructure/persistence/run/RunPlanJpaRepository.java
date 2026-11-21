package io.releasehub.infrastructure.persistence.run;

import io.releasehub.application.run.plan.RunPlanSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RunPlanJpaRepository extends JpaRepository<RunPlanJpaEntity, String> {
    Optional<RunPlanJpaEntity> findByOperatorAndIdempotencyKey(String operator, String idempotencyKey);

    @Query("SELECT p FROM RunPlanJpaEntity p WHERE p.windowId = :windowId AND p.run.runType = 'WINDOW_CLEANUP'")
    List<RunPlanJpaEntity> findCleanupByWindowId(@Param("windowId") String windowId);

    @Query("SELECT COUNT(p) FROM RunPlanJpaEntity p WHERE p.windowId = :windowId AND p.run.executionStatus IN ('RUNNING', 'NEEDS_REVIEW')")
    long countUnresolvedExecutionsByWindowId(@Param("windowId") String windowId);

    @Query("SELECT COUNT(p) FROM RunPlanJpaEntity p JOIN p.run.items i WHERE p.windowId = :windowId AND p.run.executionStatus IN ('RUNNING', 'NEEDS_REVIEW') AND i.repoId IN :repoIds")
    long countUnresolvedExecutionsForRepositories(@Param("windowId") String windowId, @Param("repoIds") List<String> repoIds);

    @Query("""
            select new io.releasehub.application.run.plan.RunPlanSummary(p.runId, p.operator, p.createdAt, p.itemCount)
            from RunPlanJpaEntity p where p.windowId = :windowId order by p.createdAt desc, p.runId desc
            """)
    List<RunPlanSummary> listSummaries(@Param("windowId") String windowId, Pageable pageable);
}
