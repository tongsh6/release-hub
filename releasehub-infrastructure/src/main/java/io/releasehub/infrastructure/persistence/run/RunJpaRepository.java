package io.releasehub.infrastructure.persistence.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RunJpaRepository extends JpaRepository<RunJpaEntity, String> {
    @Query(value = """
            select distinct r from RunJpaEntity r
            left join r.items i
            where (:runType is null or r.runType = :runType)
              and (:operator is null or r.operator = :operator)
              and (:windowKey is null or i.windowKey = :windowKey)
              and (:repoId is null or i.repoId = :repoId)
              and (:iterationKey is null or i.iterationKey = :iterationKey)
            """,
            countQuery = """
            select count(distinct r.id) from RunJpaEntity r
            left join r.items i
            where (:runType is null or r.runType = :runType)
              and (:operator is null or r.operator = :operator)
              and (:windowKey is null or i.windowKey = :windowKey)
              and (:repoId is null or i.repoId = :repoId)
              and (:iterationKey is null or i.iterationKey = :iterationKey)
            """)
    Page<RunJpaEntity> findPagedByFilters(@Param("runType") String runType,
                                          @Param("operator") String operator,
                                          @Param("windowKey") String windowKey,
                                          @Param("repoId") String repoId,
                                          @Param("iterationKey") String iterationKey,
                                          Pageable pageable);

    @Query("""
            select distinct r from RunJpaEntity r
            left join r.items i
            where (:runType is null or r.runType = :runType)
              and (:operator is null or r.operator = :operator)
              and (:windowKey is null or i.windowKey = :windowKey)
              and (:repoId is null or i.repoId = :repoId)
              and (:iterationKey is null or i.iterationKey = :iterationKey)
            """)
    List<RunJpaEntity> findAllByFilters(@Param("runType") String runType,
                                        @Param("operator") String operator,
                                        @Param("windowKey") String windowKey,
                                        @Param("repoId") String repoId,
                                        @Param("iterationKey") String iterationKey);
}
