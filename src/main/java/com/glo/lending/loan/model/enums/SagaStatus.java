package com.glo.lending.loan.model.enums;

/**
 * Tracks the distributed transaction (saga) state during loan creation.
 * <p>
 * The loan creation saga spans the Loan Service and Customer Service:
 * <ol>
 *   <li>{@link #PENDING} — Loan record created, saga not yet started</li>
 *   <li>{@link #LIMIT_RESERVED} — Customer's available limit has been reserved</li>
 *   <li>{@link #COMPLETED} — All steps succeeded, loan is fully disbursed</li>
 *   <li>{@link #COMPENSATING} — A failure occurred, compensation in progress</li>
 *   <li>{@link #FAILED} — Saga failed and compensation completed</li>
 * </ol>
 * </p>
 */
public enum SagaStatus {
    /** Loan record created, awaiting saga execution. */
    PENDING,
    /** Customer's available loan limit has been successfully reserved. */
    LIMIT_RESERVED,
    /** All saga steps completed successfully. */
    COMPLETED,
    /** A failure triggered compensation; rollback in progress. */
    COMPENSATING,
    /** Saga failed and all compensations have been applied. */
    FAILED
}

