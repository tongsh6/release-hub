-- Add expected_release_at column to iteration table
ALTER TABLE iteration ADD COLUMN IF NOT EXISTS expected_release_at TIMESTAMP WITH TIME ZONE;
