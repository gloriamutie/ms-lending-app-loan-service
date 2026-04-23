-- Add disbursement tracking to loans table
ALTER TABLE loans ADD COLUMN IF NOT EXISTS disbursed_at TIMESTAMP;

-- Update state constraint to include PENDING_DISBURSEMENT
ALTER TABLE loans DROP CONSTRAINT IF EXISTS chk_loan_state;
ALTER TABLE loans ADD CONSTRAINT chk_loan_state
    CHECK (state IN ('PENDING_DISBURSEMENT', 'OPEN', 'CLOSED', 'CANCELLED', 'OVERDUE', 'WRITTEN_OFF'));

