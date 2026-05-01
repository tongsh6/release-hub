-- Add name column to iteration table
ALTER TABLE iteration ADD COLUMN IF NOT EXISTS name VARCHAR(255);

-- Set default value for existing records (use key as name)
UPDATE iteration SET name = iteration_key WHERE name IS NULL;
