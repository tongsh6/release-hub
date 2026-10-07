-- 仅扩展新预览协议；不回填旧 Run，不修改历史执行项标识或归属。
ALTER TABLE run ADD COLUMN execution_version INTEGER;
ALTER TABLE run ADD COLUMN execution_status VARCHAR(32);

CREATE TABLE run_plan (
    run_id VARCHAR(255) PRIMARY KEY REFERENCES run(id),
    window_id VARCHAR(255) NOT NULL,
    operator VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    plan_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    item_count INTEGER NOT NULL CHECK (item_count > 0),
    snapshot TEXT NOT NULL,
    CONSTRAINT uk_run_plan_request UNIQUE (operator, idempotency_key)
);
CREATE INDEX idx_run_plan_window_created ON run_plan(window_id, created_at DESC, run_id DESC);
-- 回退时停用预览入口并保留表；窗口删除后仍保留快照，不级联删除历史预览。
