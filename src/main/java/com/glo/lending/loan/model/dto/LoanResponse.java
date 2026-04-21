package com.glo.lending.loan.model.dto;

import com.glo.lending.loan.model.enums.LoanState;
import com.glo.lending.loan.model.enums.LoanType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO representing a loan with its installments and fees.
 *
 * @param id                unique loan identifier
 * @param customerId        borrower
 * @param productId         associated product
 * @param principalAmount   original principal
 * @param outstandingBalance remaining balance
 * @param totalFees         total fees charged
 * @param loanType          LUMP_SUM or INSTALLMENT
 * @param state             current loan state
 * @param originationDate   when the loan was originated
 * @param dueDate           final due date
 * @param tenureValue       tenure value
 * @param tenureType        DAYS or MONTHS
 * @param installments      list of installments (if INSTALLMENT type)
 * @param createdAt         creation timestamp
 * @param updatedAt         last update timestamp
 */
public record LoanResponse(
        UUID id,
        UUID customerId,
        UUID productId,
        BigDecimal principalAmount,
        BigDecimal outstandingBalance,
        BigDecimal totalFees,
        LoanType loanType,
        LoanState state,
        LocalDate originationDate,
        LocalDate dueDate,
        Integer tenureValue,
        String tenureType,
        List<InstallmentResponse> installments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

