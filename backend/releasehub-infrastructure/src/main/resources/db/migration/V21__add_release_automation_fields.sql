-- 代码仓库表：添加版本号字段
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS initial_version VARCHAR(50);
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS version_source VARCHAR(20);

-- 迭代-仓库关联表：添加版本号和分支字段
ALTER TABLE iteration_repo ADD COLUMN IF NOT EXISTS base_version VARCHAR(50);
ALTER TABLE iteration_repo ADD COLUMN IF NOT EXISTS dev_version VARCHAR(50);
ALTER TABLE iteration_repo ADD COLUMN IF NOT EXISTS target_version VARCHAR(50);
ALTER TABLE iteration_repo ADD COLUMN IF NOT EXISTS feature_branch VARCHAR(200);
ALTER TABLE iteration_repo ADD COLUMN IF NOT EXISTS version_source VARCHAR(20);
ALTER TABLE iteration_repo ADD COLUMN IF NOT EXISTS version_synced_at TIMESTAMP WITH TIME ZONE;

-- 窗口-迭代关联表：添加 release 分支字段
ALTER TABLE window_iteration ADD COLUMN IF NOT EXISTS release_branch VARCHAR(200);
ALTER TABLE window_iteration ADD COLUMN IF NOT EXISTS branch_created BOOLEAN DEFAULT FALSE;
ALTER TABLE window_iteration ADD COLUMN IF NOT EXISTS last_merge_at TIMESTAMP WITH TIME ZONE;

-- 运行任务表
CREATE TABLE IF NOT EXISTS run_task (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    task_order INT NOT NULL,
    target_type VARCHAR(50),
    target_id VARCHAR(36),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_run_task_run FOREIGN KEY (run_id) REFERENCES run(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_run_task_run_id ON run_task(run_id);
CREATE INDEX IF NOT EXISTS idx_run_task_status ON run_task(status);
