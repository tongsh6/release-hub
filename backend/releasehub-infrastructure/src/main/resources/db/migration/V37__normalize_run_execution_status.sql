-- 单一 Run 状态协议：一次性归一已有研发数据，查询不再保留字符串猜测分支。
UPDATE run r
SET run_type = 'WINDOW_CLEANUP'
WHERE r.run_type = 'WINDOW_ORCHESTRATION'
  AND EXISTS (
    SELECT 1 FROM run_item i JOIN run_step s ON s.run_item_id = i.id
    WHERE i.run_id = r.id
      AND s.action_type IN ('MERGE_TO_MASTER', 'CREATE_TAG', 'TRIGGER_CI', 'CLOSE_ITERATION', 'ARCHIVE_BRANCH')
  );

-- 使用相关聚合，兼容 PostgreSQL 和测试 H2；完成判据与 Run.aggregateStatus 一致。
UPDATE run r
SET execution_status = CASE
    WHEN r.execution_status = 'PLANNED' THEN 'PLANNED'
    WHEN r.finished_at IS NULL THEN 'RUNNING'
    WHEN EXISTS (
        SELECT 1 FROM run_item i WHERE i.run_id = r.id
          AND (i.final_result = 'UNKNOWN' OR EXISTS (
              SELECT 1 FROM run_step s WHERE s.run_item_id = i.id AND s.result = 'UNKNOWN'
          ))
    ) THEN 'NEEDS_REVIEW'
    WHEN NOT EXISTS (SELECT 1 FROM run_item i WHERE i.run_id = r.id) THEN 'FAILED'
    WHEN NOT EXISTS (
        SELECT i.repo_id FROM run_item i
        JOIN run owner_run ON owner_run.id = i.run_id
        WHERE i.run_id = r.id
        GROUP BY i.repo_id
        HAVING NOT BOOL_AND(
               CASE owner_run.run_type
                   WHEN 'WINDOW_ORCHESTRATION' THEN COALESCE(i.final_result IN ('MERGED', 'ALREADY_MERGED'), FALSE)
                   WHEN 'ATTACH_ITERATION' THEN COALESCE(i.final_result IN ('MERGED', 'ALREADY_MERGED'), FALSE)
                   WHEN 'VERSION_UPDATE' THEN COALESCE(i.final_result = 'VERSION_UPDATE_SUCCESS', FALSE)
                   WHEN 'WINDOW_CLEANUP' THEN COALESCE(i.final_result IN ('SUCCESS', 'CI_NOT_CONFIGURED'), FALSE)
                   ELSE FALSE
               END AND NOT EXISTS (
                   SELECT 1 FROM run_step s WHERE s.run_item_id = i.id
                     AND s.result IN ('FAILED', 'VERSION_UPDATE_FAILED', 'MERGE_BLOCKED', 'SKIPPED_DUE_TO_BLOCK')
               )
        )
    ) THEN 'DONE'
    WHEN NOT EXISTS (
        SELECT i.repo_id FROM run_item i
        JOIN run owner_run ON owner_run.id = i.run_id
        WHERE i.run_id = r.id
        GROUP BY i.repo_id
        HAVING BOOL_AND(
               CASE owner_run.run_type
                   WHEN 'WINDOW_ORCHESTRATION' THEN COALESCE(i.final_result IN ('MERGED', 'ALREADY_MERGED'), FALSE)
                   WHEN 'ATTACH_ITERATION' THEN COALESCE(i.final_result IN ('MERGED', 'ALREADY_MERGED'), FALSE)
                   WHEN 'VERSION_UPDATE' THEN COALESCE(i.final_result = 'VERSION_UPDATE_SUCCESS', FALSE)
                   WHEN 'WINDOW_CLEANUP' THEN COALESCE(i.final_result IN ('SUCCESS', 'CI_NOT_CONFIGURED'), FALSE)
                   ELSE FALSE
               END AND NOT EXISTS (
                   SELECT 1 FROM run_step s WHERE s.run_item_id = i.id
                     AND s.result IN ('FAILED', 'VERSION_UPDATE_FAILED', 'MERGE_BLOCKED', 'SKIPPED_DUE_TO_BLOCK')
               )
        )
    ) THEN 'FAILED'
    ELSE 'PARTIAL_FAILED'
END;
ALTER TABLE run ALTER COLUMN execution_status SET NOT NULL;
CREATE INDEX idx_run_execution_status_started ON run(execution_status, started_at DESC);
