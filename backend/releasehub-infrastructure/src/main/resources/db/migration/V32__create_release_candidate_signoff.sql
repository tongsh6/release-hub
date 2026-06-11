CREATE TABLE release_candidate_signoff (
    id VARCHAR(64) PRIMARY KEY,
    candidate_id VARCHAR(128) NOT NULL,
    candidate_label VARCHAR(200) NOT NULL,
    reviewer VARCHAR(128) NOT NULL,
    decision VARCHAR(64) NOT NULL,
    note TEXT,
    checklist_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_release_candidate_signoff_candidate_created
    ON release_candidate_signoff(candidate_id, created_at DESC);
