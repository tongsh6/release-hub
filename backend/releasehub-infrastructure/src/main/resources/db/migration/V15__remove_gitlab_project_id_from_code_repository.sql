-- Remove gitlabProjectId field from code_repository table
ALTER TABLE code_repository DROP CONSTRAINT IF EXISTS uk_code_repository_gitlab_project_id;
ALTER TABLE code_repository DROP COLUMN IF EXISTS gitlab_project_id;
