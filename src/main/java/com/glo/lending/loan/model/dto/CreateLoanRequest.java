package com.glo.lending.loan.model.dto;

import com.glo.lending.loan.model.enums.LoanType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating/disbursing a new loan.
 *
 * @param idempotencyKey  client-generated unique key to prevent duplicate loan creation
 * @param customerId      the borrowing customer
 * @param productId       the loan product to use
 * @param principalAmount the loan principal
 * @param loanType        LUMP_SUM or INSTALLMENT
 * @param tenureValue     numeric tenure value
 * @param tenureType      DAYS or MONTHS
 * @param billingCycleId  optional billing cycle for consolidated billing
 */
public record CreateLoanRequest(
        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey,

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Principal amount is required")
        @DecimalMin(value = "0.01", message = "Principal must be greater than zero")
        BigDecimal principalAmount,

        @NotNull(message = "Loan type is required")
        LoanType loanType,

        @NotNull(message = "Tenure value is required")
        @Min(value = 1, message = "Tenure must be at least 1")
        Integer tenureValue,

        @NotBlank(message = "Tenure type is required")
        String tenureType,

        UUID billingCycleId
) {
}

