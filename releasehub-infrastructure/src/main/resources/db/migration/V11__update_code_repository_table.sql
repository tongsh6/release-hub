ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS branch_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS active_branch_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS non_compliant_branch_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS mr_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS open_mr_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS merged_mr_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS closed_mr_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMP WITH TIME ZONE;
