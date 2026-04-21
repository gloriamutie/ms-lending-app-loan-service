-- =====================================================
-- Loan Service Schema
-- Database: PostgreSQL
-- =====================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Billing Cycles (consolidated due dates)
CREATE TABLE IF NOT EXISTS billing_cycles (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id           UUID           NOT NULL,
    consolidated_due_day  INT            NOT NULL,
    is_consolidated       BOOLEAN        NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_due_day CHECK (consolidated_due_day BETWEEN 1 AND 28)
);

-- Loans
CREATE TABLE IF NOT EXISTS loans (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id         UUID           NOT NULL,
    product_id          UUID           NOT NULL,
    principal_amount    NUMERIC(15,2)  NOT NULL,
    outstanding_balance NUMERIC(15,2)  NOT NULL,
    total_fees          NUMERIC(15,2)  NOT NULL DEFAULT 0.00,
    loan_type           VARCHAR(20)    NOT NULL,
    state               VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    origination_date    DATE           NOT NULL,
    due_date            DATE           NOT NULL,
    billing_cycle_id    UUID           REFERENCES billing_cycles(id),
    tenure_value        INT            NOT NULL,
    tenure_type         VARCHAR(10)    NOT NULL,
    idempotency_key     VARCHAR(100)   UNIQUE,
    saga_status         VARCHAR(20)    DEFAULT 'PENDING',
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_loan_type CHECK (loan_type IN ('LUMP_SUM', 'INSTALLMENT')),
    CONSTRAINT chk_loan_state CHECK (state IN ('OPEN', 'CLOSED', 'CANCELLED', 'OVERDUE', 'WRITTEN_OFF')),
    CONSTRAINT chk_tenure_type CHECK (tenure_type IN ('DAYS', 'MONTHS')),
    CONSTRAINT chk_principal CHECK (principal_amount > 0),
    CONSTRAINT chk_saga_status CHECK (saga_status IN ('PENDING', 'LIMIT_RESERVED', 'COMPLETED', 'COMPENSATING', 'FAILED'))
);

-- Loan Installments
CREATE TABLE IF NOT EXISTS loan_installments (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    loan_id             UUID           NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    installment_number  INT            NOT NULL,
    amount              NUMERIC(15,2)  NOT NULL,
    paid_amount         NUMERIC(15,2)  NOT NULL DEFAULT 0.00,
    due_date            DATE           NOT NULL,
    state               VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    paid_at             TIMESTAMP,

    CONSTRAINT chk_installment_state CHECK (state IN ('PENDING', 'DUE', 'PAID', 'OVERDUE', 'PARTIALLY_PAID')),
    CONSTRAINT chk_installment_amount CHECK (amount > 0)
);

-- Loan Repayments
CREATE TABLE IF NOT EXISTS loan_repayments (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    loan_id             UUID           NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    installment_id      UUID           REFERENCES loan_installments(id),
    amount              NUMERIC(15,2)  NOT NULL,
    payment_date        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_reference   VARCHAR(100)   NOT NULL,

    CONSTRAINT chk_repayment_amount CHECK (amount > 0)
);

-- Loan Fees (actual fees charged on loans)
CREATE TABLE IF NOT EXISTS loan_fees (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    loan_id         UUID           NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    fee_type        VARCHAR(20)    NOT NULL,
    amount          NUMERIC(15,2)  NOT NULL,
    applied_date    DATE           NOT NULL DEFAULT CURRENT_DATE,
    is_paid         BOOLEAN        NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_loan_fee_type CHECK (fee_type IN ('SERVICE_FEE', 'DAILY_FEE', 'LATE_FEE')),
    CONSTRAINT chk_loan_fee_amount CHECK (amount > 0)
);

CREATE INDEX idx_loans_customer ON loans(customer_id);
CREATE INDEX idx_loans_state ON loans(state);
CREATE INDEX idx_loans_due_date ON loans(due_date);
CREATE INDEX idx_loans_billing_cycle ON loans(billing_cycle_id);
CREATE UNIQUE INDEX idx_loans_idempotency_key ON loans(idempotency_key);
CREATE INDEX idx_loans_saga_status ON loans(saga_status);
CREATE INDEX idx_installments_loan ON loan_installments(loan_id);
CREATE INDEX idx_installments_state ON loan_installments(state);
CREATE INDEX idx_installments_due_date ON loan_installments(due_date);
CREATE INDEX idx_repayments_loan ON loan_repayments(loan_id);
CREATE INDEX idx_loan_fees_loan ON loan_fees(loan_id);
CREATE INDEX idx_billing_cycles_customer ON billing_cycles(customer_id);

