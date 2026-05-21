ALTER TABLE version_policy
    ADD COLUMN scope_level VARCHAR(32) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE version_policy
    ADD COLUMN scope_project_id VARCHAR(128);

ALTER TABLE version_policy
    ADD COLUMN scope_sub_project_id VARCHAR(128);

CREATE INDEX idx_version_policy_scope ON version_policy(scope_level, scope_project_id, scope_sub_project_id);
