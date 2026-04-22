-- Remove saga-related column and index from loans table
DROP INDEX IF EXISTS idx_loans_saga_status;
ALTER TABLE loans DROP COLUMN IF EXISTS saga_status;

