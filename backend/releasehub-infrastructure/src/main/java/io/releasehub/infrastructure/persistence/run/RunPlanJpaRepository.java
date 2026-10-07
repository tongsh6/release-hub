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

    @Query("""
            select new io.releasehub.application.run.plan.RunPlanSummary(p.runId, p.operator, p.createdAt, p.itemCount)
            from RunPlanJpaEntity p where p.windowId = :windowId order by p.createdAt desc, p.runId desc
            """)
    List<RunPlanSummary> listSummaries(@Param("windowId") String windowId, Pageable pageable);
}
