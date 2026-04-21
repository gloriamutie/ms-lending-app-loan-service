package com.glo.lending.loan.model.enums;

/**
 * Represents the payment state of an individual loan installment.
 */
public enum InstallmentState {
    /** Installment is pending and not yet due. */
    PENDING,
    /** Installment is due but not yet paid. */
    DUE,
    /** Installment has been fully paid. */
    PAID,
    /** Installment is past its due date and unpaid. */
    OVERDUE,
    /** Installment has been partially paid. */
    PARTIALLY_PAID
}

