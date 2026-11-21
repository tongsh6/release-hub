-- Run 详情按来源分页读取操作，避免记录增长后扫描整个操作账本。
CREATE INDEX idx_git_operation_run ON git_operation(run_id, created_at DESC, id DESC);
