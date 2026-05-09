ALTER TABLE iteration_repo ADD COLUMN IF NOT EXISTS branch_creation_mode VARCHAR(16) DEFAULT 'AUTO';
