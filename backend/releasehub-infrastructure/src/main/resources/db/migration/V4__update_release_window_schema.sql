-- Add published_at column
ALTER TABLE release_window ADD COLUMN published_at TIMESTAMP;
ALTER TABLE release_window ADD COLUMN window_key VARCHAR(64);

-- If there were 'SUBMITTED' status, migrate to 'PUBLISHED' (Optional, depending on if we care about existing data)
UPDATE release_window SET status = 'PUBLISHED' WHERE status = 'SUBMITTED';
