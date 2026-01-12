-- 将 run_step.message 字段从 VARCHAR(255) 改为 TEXT，以支持存储 diff 信息
ALTER TABLE run_step ALTER COLUMN message TYPE TEXT;
