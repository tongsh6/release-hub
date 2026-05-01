-- Enlarge iteration text fields to support longer content
-- name: 255 -> 500 characters
-- description: VARCHAR -> TEXT (unlimited)
-- expected_release_at: TIMESTAMP -> DATE (only need date precision)

ALTER TABLE iteration ALTER COLUMN name TYPE VARCHAR(500);
ALTER TABLE iteration ALTER COLUMN description TYPE TEXT;
ALTER TABLE iteration ALTER COLUMN expected_release_at TYPE DATE;