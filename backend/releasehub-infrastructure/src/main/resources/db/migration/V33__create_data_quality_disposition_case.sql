CREATE TABLE data_quality_disposition_case (
    id VARCHAR(64) PRIMARY KEY,
    case_key VARCHAR(128) NOT NULL UNIQUE,
    source_report TEXT,
    data_namespace VARCHAR(128),
    review_batch_id VARCHAR(128),
    asset_scope VARCHAR(64),
    retention_policy VARCHAR(128),
    resource_type VARCHAR(128) NOT NULL,
    resource_id VARCHAR(256) NOT NULL,
    risk_type VARCHAR(128) NOT NULL,
    disposition_level VARCHAR(64) NOT NULL,
    application_entry TEXT,
    allowed_action TEXT,
    pre_execution_check TEXT,
    post_execution_verification TEXT,
    rollback_boundary TEXT,
    audit_record TEXT,
    action_snapshot TEXT NOT NULL,
    pre_state_snapshot TEXT,
    post_state_snapshot TEXT,
    status VARCHAR(64) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    handled_by VARCHAR(128),
    verified_by VARCHAR(128),
    failure_reason TEXT,
    rollback_note TEXT,
    retry_of_case_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    verified_at TIMESTAMP,
    failed_at TIMESTAMP,
    cancelled_at TIMESTAMP
);

CREATE INDEX idx_data_quality_disposition_case_created
    ON data_quality_disposition_case(created_at DESC);

CREATE INDEX idx_data_quality_disposition_case_resource
    ON data_quality_disposition_case(resource_type, resource_id, risk_type);
