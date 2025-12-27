CREATE TABLE project (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE code_repository (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    clone_url VARCHAR(255) NOT NULL,
    default_branch VARCHAR(255),
    mono_repo BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE run (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    run_type VARCHAR(50) NOT NULL,
    operator VARCHAR(255) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE run_item (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL,
    window_key VARCHAR(255) NOT NULL,
    repo_id VARCHAR(255) NOT NULL,
    iteration_key VARCHAR(255) NOT NULL,
    planned_order INT NOT NULL,
    executed_order INT NOT NULL,
    final_result VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_run_item_run FOREIGN KEY (run_id) REFERENCES run(id) ON DELETE CASCADE
);

CREATE TABLE run_step (
    run_item_id VARCHAR(255) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    result VARCHAR(50) NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE,
    message TEXT,
    CONSTRAINT fk_run_step_run_item FOREIGN KEY (run_item_id) REFERENCES run_item(id) ON DELETE CASCADE
);
