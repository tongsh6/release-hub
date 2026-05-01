ALTER TABLE release_window ADD COLUMN group_code VARCHAR(64);
ALTER TABLE iteration ADD COLUMN group_code VARCHAR(64);
ALTER TABLE code_repository ADD COLUMN group_code VARCHAR(64);
