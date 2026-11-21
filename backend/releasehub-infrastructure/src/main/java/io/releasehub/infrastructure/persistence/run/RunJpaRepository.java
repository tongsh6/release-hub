package io.releasehub.infrastructure.persistence.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RunJpaRepository extends JpaRepository<RunJpaEntity, String> {
    @Query("""
            select distinct r from RunJpaEntity r
            left join fetch r.items i
            where i.windowKey = :windowKey
            order by r.startedAt asc
            """)
    List<RunJpaEntity> findByWindowKey(@Param("windowKey") String windowKey);

    @Query(value = """
            select distinct r from RunJpaEntity r left join r.items i
            where (:runType is null or r.runType = :runType)
              and (:operator is null or r.operator = :operator)
              and (:windowKey is null or i.windowKey = :windowKey)
              and (:repoId is null or i.repoId = :repoId)
              and (:iterationKey is null or i.iterationKey = :iterationKey)
              and (:status is null or r.executionStatus = :status)
              and (:groupCode is null or exists (
                select 1 from ReleaseWindowJpaEntity w
                where w.windowKey = i.windowKey and w.groupCode = :groupCode
              ))
            """,
            countQuery = """
            select count(distinct r.id) from RunJpaEntity r left join r.items i
            where (:runType is null or r.runType = :runType)
              and (:operator is null or r.operator = :operator)
              and (:windowKey is null or i.windowKey = :windowKey)
              and (:repoId is null or i.repoId = :repoId)
              and (:iterationKey is null or i.iterationKey = :iterationKey)
              and (:status is null or r.executionStatus = :status)
              and (:groupCode is null or exists (
                select 1 from ReleaseWindowJpaEntity w
                where w.windowKey = i.windowKey and w.groupCode = :groupCode
              ))
            """)
    Page<RunJpaEntity> findPagedByFilters(@Param("runType") String runType,
                                         @Param("operator") String operator,
                                         @Param("windowKey") String windowKey,
                                         @Param("repoId") String repoId,
                                         @Param("iterationKey") String iterationKey,
                                         @Param("status") String status,
                                         @Param("groupCode") String groupCode,
                                         Pageable pageable);
}