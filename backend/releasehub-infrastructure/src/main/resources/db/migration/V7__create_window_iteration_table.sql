CREATE TABLE window_iteration (
    id VARCHAR(200) PRIMARY KEY,
    window_id VARCHAR(64) NOT NULL,
    iteration_key VARCHAR(64) NOT NULL,
    attach_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_window_iteration_window
        FOREIGN KEY (window_id) REFERENCES release_window(id)
);

CREATE UNIQUE INDEX ux_window_iteration_pair ON window_iteration(window_id, iteration_key);
CREATE INDEX idx_window_iteration_window ON window_iteration(window_id);

