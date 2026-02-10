-- V24: Add status column to iteration table
-- Adds status field to track iteration lifecycle (ACTIVE -> CLOSED)

ALTER TABLE iteration ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';

-- Update existing records to ACTIVE
UPDATE iteration SET status = 'ACTIVE' WHERE status IS NULL;
