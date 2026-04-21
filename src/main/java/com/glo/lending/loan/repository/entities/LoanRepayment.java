package com.glo.lending.loan.repository.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records a repayment transaction against a loan or a specific installment.
 */
@Data
@Table("loan_repayments")
public class LoanRepayment {

    @Id
    private UUID id;

    @Column("loan_id")
    private UUID loanId;

    @Column("installment_id")
    private UUID installmentId;

    @Column("amount")
    private BigDecimal amount;

    @Column("payment_date")
    private LocalDateTime paymentDate;

    @Column("payment_reference")
    private String paymentReference;

}

