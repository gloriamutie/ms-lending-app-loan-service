package com.glo.lending.loan.model.dto;

import com.glo.lending.loan.model.enums.InstallmentState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a loan installment.
 *
 * @param id                 unique installment identifier
 * @param installmentNumber  sequence number
 * @param amount             installment amount
 * @param paidAmount         amount paid so far
 * @param dueDate            installment due date
 * @param state              current installment state
 * @param paidAt             when fully paid (null if not yet)
 */
public record InstallmentResponse(
        UUID id,
        Integer installmentNumber,
        BigDecimal amount,
        BigDecimal paidAmount,
        LocalDate dueDate,
        InstallmentState state,
        LocalDateTime paidAt
) {
}

