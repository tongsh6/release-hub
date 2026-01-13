-- Remove projectId field from code_repository table
ALTER TABLE code_repository DROP COLUMN IF EXISTS project_id;
