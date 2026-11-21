-- 新旧写入口共用的独立证据；不回填历史操作，不关联未提交的 Run。
CREATE INDEX IF NOT EXISTS idx_code_repository_clone_provider ON code_repository(clone_url, git_provider);
CREATE TABLE git_operation (
    id VARCHAR(36) PRIMARY KEY, repo_id VARCHAR(255) NOT NULL,
    remote_key VARCHAR(64) NOT NULL, provider VARCHAR(16) NOT NULL,
    base_url VARCHAR(512) NOT NULL, project_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL, target_json TEXT NOT NULL, request_hash VARCHAR(64) NOT NULL,
    run_id VARCHAR(255), operator VARCHAR(128) NOT NULL, session_id VARCHAR(36) NOT NULL,
    attempt_id VARCHAR(36) NOT NULL UNIQUE, generation BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL, state_version BIGINT NOT NULL DEFAULT 0, reason VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_git_operation_repo ON git_operation(repo_id, created_at DESC, id DESC);
CREATE INDEX idx_git_operation_session ON git_operation(session_id);
CREATE INDEX idx_git_operation_remote ON git_operation(remote_key);
CREATE TABLE git_operation_event (
    id VARCHAR(36) PRIMARY KEY, operation_id VARCHAR(36) NOT NULL REFERENCES git_operation(id),
    attempt_id VARCHAR(36) NOT NULL, event_sequence INT NOT NULL, event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, evidence_json TEXT NOT NULL, reason VARCHAR(128),
    UNIQUE(attempt_id, event_sequence)
);
CREATE INDEX idx_git_event_operation ON git_operation_event(operation_id, event_sequence);
CREATE TABLE repo_execution_guard (
    remote_key VARCHAR(64) PRIMARY KEY, session_id VARCHAR(36), generation BIGINT NOT NULL,
    operation_id VARCHAR(36), reason VARCHAR(128), updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_git_guard_session ON repo_execution_guard(session_id);
CREATE TABLE git_operation_observation (
    id VARCHAR(36) PRIMARY KEY, operation_id VARCHAR(36) NOT NULL REFERENCES git_operation(id),
    operator VARCHAR(128) NOT NULL, request_key VARCHAR(128) NOT NULL, expected_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL, evidence_json TEXT NOT NULL, observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(operation_id, operator, request_key)
);
CREATE INDEX idx_git_observation_operation ON git_operation_observation(operation_id, observed_at DESC);
