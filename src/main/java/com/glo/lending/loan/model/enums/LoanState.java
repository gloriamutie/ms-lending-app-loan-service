package com.glo.lending.loan.model.enums;

/**
 * Represents the lifecycle state of a loan.
 */
public enum LoanState {
    /** Loan is active and in good standing. */
    OPEN,
    /** Loan is fully repaid and closed. */
    CLOSED,
    /** Loan was cancelled before disbursement or during grace period. */
    CANCELLED,
    /** Loan has past-due payments. */
    OVERDUE,
    /** Loan is considered uncollectable and written off. */
    WRITTEN_OFF
}

