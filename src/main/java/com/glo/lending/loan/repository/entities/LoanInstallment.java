package com.glo.lending.loan.repository.entities;

import com.glo.lending.loan.model.enums.InstallmentState;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;


@Data
@Table("loan_installments")
public class LoanInstallment {

    @Id
    private UUID id;

    @Column("loan_id")
    private UUID loanId;

    @Column("installment_number")
    private Integer installmentNumber;

    @Column("amount")
    private BigDecimal amount;

    @Column("paid_amount")
    private BigDecimal paidAmount;

    @Column("due_date")
    private LocalDate dueDate;

    @Column("state")
    private InstallmentState state;

    @Column("paid_at")
    private LocalDateTime paidAt;

}

