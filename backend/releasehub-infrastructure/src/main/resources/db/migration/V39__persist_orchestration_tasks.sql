-- 复用 run_task 的固定动作与 Item 目标列；不回填历史 Run 的执行意图。
ALTER TABLE run_task ALTER COLUMN run_id TYPE VARCHAR(255);
ALTER TABLE run_task ADD COLUMN claim_token VARCHAR(36);
CREATE UNIQUE INDEX uk_run_task_item_action ON run_task(run_id, target_type, target_id, task_type);

-- 新协议 Step 有稳定任务身份；旧步骤仍保持原有审计事实。
ALTER TABLE run_step ADD COLUMN task_id VARCHAR(36) REFERENCES run_task(id);
CREATE UNIQUE INDEX uk_run_step_task ON run_step(task_id);

-- 每个 C1 动作最多一次外部写；纯读取任务没有 operation。
ALTER TABLE git_operation ADD COLUMN task_id VARCHAR(36) REFERENCES run_task(id);
ALTER TABLE git_operation ADD COLUMN claim_token VARCHAR(36);
ALTER TABLE git_operation ADD CONSTRAINT ck_git_operation_task_claim
    CHECK ((task_id IS NULL AND claim_token IS NULL) OR (task_id IS NOT NULL AND claim_token IS NOT NULL));
CREATE UNIQUE INDEX uk_git_operation_task ON git_operation(task_id);

-- 回退先停止任务派发；保留任务、Step、operation 和仓库阻断，不恢复旧循环或自动重放。
