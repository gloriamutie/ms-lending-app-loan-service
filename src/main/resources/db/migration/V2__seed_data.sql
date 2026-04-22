-- =====================================================
-- Loan Service Seed Data
-- =====================================================

-- Billing cycle for Jane Wanjiku (consolidated)
INSERT INTO billing_cycles (id, customer_id, consolidated_due_day, is_consolidated)
VALUES ('a1111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'd1e2f3a4-b5c6-7890-def1-234567890abc', 15, TRUE)
ON CONFLICT (id) DO NOTHING;

-- Loan 1: Active lump-sum loan for Jane
INSERT INTO loans (id, customer_id, product_id, principal_amount, outstanding_balance, total_fees, loan_type, state, origination_date, due_date, billing_cycle_id, tenure_value, tenure_type)
VALUES ('b1111111-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'd1e2f3a4-b5c6-7890-def1-234567890abc', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 10000.00, 10500.00, 500.00, 'LUMP_SUM', 'OPEN', '2026-03-20', '2026-04-19', 'a1111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 30, 'DAYS')
ON CONFLICT (id) DO NOTHING;

-- Service fee applied to Loan 1
INSERT INTO loan_fees (id, loan_id, fee_type, amount, applied_date, is_paid)
VALUES ('c1111111-cccc-cccc-cccc-cccccccccccc', 'b1111111-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'SERVICE_FEE', 500.00, '2026-03-20', FALSE)
ON CONFLICT (id) DO NOTHING;

-- Loan 2: Installment loan for John Kamau
INSERT INTO loans (id, customer_id, product_id, principal_amount, outstanding_balance, total_fees, loan_type, state, origination_date, due_date, tenure_value, tenure_type)
VALUES ('b2222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'd2e3f4a5-b6c7-8901-def2-345678901bcd', 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 60000.00, 60000.00, 1500.00, 'INSTALLMENT', 'OPEN', '2026-01-15', '2026-04-15', 3, 'MONTHS')
ON CONFLICT (id) DO NOTHING;

-- Installments for Loan 2
INSERT INTO loan_installments (id, loan_id, installment_number, amount, paid_amount, due_date, state)
VALUES ('d1111111-dddd-dddd-dddd-dddddddddddd', 'b2222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 1, 20000.00, 20000.00, '2026-02-15', 'PAID')
ON CONFLICT (id) DO NOTHING;

INSERT INTO loan_installments (id, loan_id, installment_number, amount, paid_amount, due_date, state)
VALUES ('d2222222-dddd-dddd-dddd-dddddddddddd', 'b2222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 2, 20000.00, 0.00, '2026-03-15', 'OVERDUE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO loan_installments (id, loan_id, installment_number, amount, paid_amount, due_date, state)
VALUES ('d3333333-dddd-dddd-dddd-dddddddddddd', 'b2222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 3, 20000.00, 0.00, '2026-04-15', 'PENDING')
ON CONFLICT (id) DO NOTHING;

-- Repayment for first installment of Loan 2
INSERT INTO loan_repayments (id, loan_id, installment_id, amount, payment_date, payment_reference)
VALUES ('e1111111-eeee-eeee-eeee-eeeeeeeeeeee', 'b2222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'd1111111-dddd-dddd-dddd-dddddddddddd', 20000.00, '2026-02-14 09:30:00', 'MPESA-REF-001')
ON CONFLICT (id) DO NOTHING;

-- Loan 3: Overdue loan (for sweep job demo)
INSERT INTO loans (id, customer_id, product_id, principal_amount, outstanding_balance, total_fees, loan_type, state, origination_date, due_date, tenure_value, tenure_type, idempotency_key)
VALUES ('b3333333-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'd2e3f4a5-b6c7-8901-def2-345678901bcd', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 5000.00, 5750.00, 750.00, 'LUMP_SUM', 'OVERDUE', '2026-02-01', '2026-03-03', 30, 'DAYS', 'IDEM-JOHN-LOAN-002')
ON CONFLICT (id) DO NOTHING;

INSERT INTO loan_fees (id, loan_id, fee_type, amount, applied_date, is_paid)
VALUES ('c2222222-cccc-cccc-cccc-cccccccccccc', 'b3333333-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'LATE_FEE', 500.00, '2026-03-06', FALSE)
ON CONFLICT (id) DO NOTHING;

