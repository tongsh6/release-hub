-- 期限只允许竞争接管；原 token 和外部事实继续由现有门禁约束。
ALTER TABLE run_task ADD COLUMN claim_expires_at TIMESTAMP WITH TIME ZONE;

-- 不从应用时钟的首次 started_at 推断旧领取的期限；NULL 保持不可自动接管。
