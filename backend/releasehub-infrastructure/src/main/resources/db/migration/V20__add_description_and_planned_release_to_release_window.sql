-- Add description and planned_release_at columns to release_window table

ALTER TABLE release_window ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE release_window ADD COLUMN IF NOT EXISTS planned_release_at TIMESTAMP WITH TIME ZONE;
