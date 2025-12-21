-- Ensure schema exists
CREATE SCHEMA IF NOT EXISTS release_hub;

-- Note: Cross-schema move (ALTER TABLE ... SET SCHEMA) is PostgreSQL-specific and
-- not supported by H2. To keep migrations portable for tests, we skip moving existing
-- tables from public to release_hub here. Fresh schemas will be created below.

-- Create tables if they do not exist (idempotent)
CREATE TABLE IF NOT EXISTS release_hub.iteration (
    iteration_key VARCHAR(64) PRIMARY KEY,
    description VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS release_hub.iteration_repo (
    iteration_key VARCHAR(64) NOT NULL,
    repo_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (iteration_key, repo_id),
    CONSTRAINT fk_iteration_repo_iteration FOREIGN KEY (iteration_key) REFERENCES release_hub.iteration(iteration_key)
);

-- Skip column renames for portability across H2/PostgreSQL in tests.
-- If legacy columns exist in Postgres, run manual renames:
-- ALTER TABLE release_hub.iteration RENAME COLUMN key TO iteration_key;
-- ALTER TABLE release_hub.iteration_repo RENAME COLUMN "iterationKey" TO iteration_key;

-- Ensure index exists
CREATE INDEX IF NOT EXISTS idx_iteration_repo_iteration_key ON release_hub.iteration_repo(iteration_key);
