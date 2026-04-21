package com.glo.lending.loan.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for making a loan repayment.
 *
 * @param loanId           the loan to repay
 * @param installmentId    optional specific installment to repay
 * @param amount           repayment amount
 * @param paymentReference external payment reference (e.g., M-Pesa code)
 */
public record RepaymentRequest(
        @NotNull(message = "Loan ID is required")
        UUID loanId,

        UUID installmentId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Repayment amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Payment reference is required")
        String paymentReference
) {
}

