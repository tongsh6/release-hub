package io.releasehub.infrastructure.persistence.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RunJpaRepository extends JpaRepository<RunJpaEntity, String> {
    @Query(value = """
            select distinct r from RunJpaEntity r
            left join r.items i
            where (:runType is null or r.runType = :runType)
              and (:operator is null or r.operator = :operator)
              and (:windowKey is null or i.windowKey = :windowKey)
              and (:repoId is null or i.repoId = :repoId)
              and (:iterationKey is null or i.iterationKey = :iterationKey)
              and (
                :status is null or
                (
                  (:status = 'FAILED' and exists (
                     select 1 from RunItemJpaEntity item
                     where item.run.id = r.id
                       and (item.finalResult like '%FAILED%' or item.finalResult = 'MERGE_BLOCKED')
                   )
                  ) or
                  (:status = 'SUCCESS' and exists (
                     select 1 from RunItemJpaEntity item where item.run.id = r.id
                   ) and not exists (
                     select 1 from RunItemJpaEntity item
                     where item.run.id = r.id
                       and (item.finalResult is null or item.finalResult not like '%SUCCESS%')
                   )
                   and not exists (
                     select 1 from RunItemJpaEntity item
                     where item.run.id = r.id
                       and (item.finalResult like '%FAILED%' or item.finalResult = 'MERGE_BLOCKED')
                   )
                  ) or
                  (:status = 'COMPLETED' and r.finishedAt is not null and
                   not exists (
                     select 1 from RunItemJpaEntity item
                     where item.run.id = r.id
                       and (item.finalResult like '%FAILED%' or item.finalResult = 'MERGE_BLOCKED')
                   )
                   and not (
                     exists (select 1 from RunItemJpaEntity item where item.run.id = r.id)
                     and not exists (
                       select 1 from RunItemJpaEntity item
                       where item.run.id = r.id
                         and (item.finalResult is null or item.finalResult not like '%SUCCESS%')
                     )
                   )
                  ) or
                  (:status = 'RUNNING' and r.finishedAt is null and
                   not exists (
                     select 1 from RunItemJpaEntity item
                     where item.run.id = r.id
                       and (item.finalResult like '%FAILED%' or item.finalResult = 'MERGE_BLOCKED')
                   )
                  )
                )
              )
            """,
            countQuery = """
                    select count(distinct r.id) from RunJpaEntity r
                    left join r.items i
                    where (:runType is null or r.runType = :runType)
                      and (:operator is null or r.operator = :operator)
                      and (:windowKey is null or i.windowKey = :windowKey)
                      and (:repoId is null or i.repoId = :repoId)
                      and (:iterationKey is null or i.iterationKey = :iterationKey)
                      and (
                        :status is null or
                        (
                          (:status = 'FAILED' and exists (
                             select 1 from RunItemJpaEntity item
                             where item.run.id = r.id
                               and (item.finalResult like '%FAILED%' or item.finalResult = 'MERGE_BLOCKED')
                           )
                          ) or
                          (:status = 'SUCCESS' and exists (
                             select 1 from RunItemJpaEntity item where item.run.id = r.id
                           ) and not exists (
                             select 1 from RunItemJpaEntity item
                             where item.run.id = r.id
                               and (item.finalResult is null or item.finalResult not like '%SUCCESS%')
                           )
                           and not exists (
                             select 1 from RunItemJpaEntity item
                             where item.run.id = r.id
                               and (item.finalResult like '%FAILED%' or item.finalResult = 'MERGE_BLOCKED')
                           )
                          ) or
                          (:status = 'COMPLETED' and r.finishedAt is not null and
                           not exists (
                             select 1 from RunItemJpaEntity item
                             where item.run.id = r.id
                               and (item.finalResult like '%FAILED%' or item.finalResult = 'MERGE_BLOCKED')
                           )
                           and not (
                             exists (select 1 from RunItemJpaEntity item where item.run.id = r.id)
                             and not exists (
                               select 1 from RunItemJpaEntity item
                               where item.run.id = r.id
                                 and (item.finalResult is null or item.finalResult not like '%SUCCESS%')
                             )
                           )
                          ) or
                          (:status = 'RUNNING' and r.finishedAt is null and
                           not exists (
                             select 1 from RunItemJpaEntity item
                             where item.run.id = r.id
                               and (item.finalResult like '%FAILED%' or item.finalResult = 'MERGE_BLOCKED')
                           )
                          )
                        )
                      )
                    """)
    Page<RunJpaEntity> findPagedByFilters(@Param("runType") String runType,
                                          @Param("operator") String operator,
                                          @Param("windowKey") String windowKey,
                                          @Param("repoId") String repoId,
                                          @Param("iterationKey") String iterationKey,
                                          @Param("status") String status,
                                          Pageable pageable);
}
