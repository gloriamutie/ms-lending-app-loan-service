package com.glo.lending.loan.repository.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Table("loan_fees")
public class LoanFee {

    @Id
    private UUID id;

    @Column("loan_id")
    private UUID loanId;

    @Column("fee_type")
    private String feeType;

    @Column("amount")
    private BigDecimal amount;

    @Column("applied_date")
    private LocalDate appliedDate;

    @Column("is_paid")
    private Boolean isPaid;

}

