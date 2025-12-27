DELETE FROM code_repository;

ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS gitlab_project_id BIGINT;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS branch_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS active_branch_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS non_compliant_branch_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS mr_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS open_mr_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS merged_mr_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS closed_mr_count INT DEFAULT 0;
ALTER TABLE code_repository ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE code_repository ALTER COLUMN gitlab_project_id SET NOT NULL;

ALTER TABLE code_repository DROP CONSTRAINT IF EXISTS uk_code_repository_gitlab_project_id;
ALTER TABLE code_repository ADD CONSTRAINT uk_code_repository_gitlab_project_id UNIQUE (gitlab_project_id);
