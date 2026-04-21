package com.glo.lending.loan.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a repayment transaction.
 *
 * @param id               unique repayment identifier
 * @param loanId           associated loan
 * @param installmentId    associated installment (if applicable)
 * @param amount           amount paid
 * @param paymentDate      when payment was processed
 * @param paymentReference external reference
 */
public record RepaymentResponse(
        UUID id,
        UUID loanId,
        UUID installmentId,
        BigDecimal amount,
        LocalDateTime paymentDate,
        String paymentReference
) {
}

