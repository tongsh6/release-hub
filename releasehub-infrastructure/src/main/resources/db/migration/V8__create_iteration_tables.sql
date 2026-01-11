CREATE TABLE iteration (
    iteration_key VARCHAR(64) PRIMARY KEY,
    description VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE iteration_repo (
    iteration_key VARCHAR(64) NOT NULL,
    repo_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (iteration_key, repo_id),
    CONSTRAINT fk_iteration_repo_iteration FOREIGN KEY (iteration_key) REFERENCES iteration(iteration_key)
);

CREATE INDEX idx_iteration_repo_iteration_key ON iteration_repo(iteration_key);
