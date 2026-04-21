package com.glo.lending.loan.repository.entities;

import com.glo.lending.loan.model.enums.LoanState;
import com.glo.lending.loan.model.enums.LoanType;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;


@Data
@Table("loans")
public class Loan {

    @Id
    private UUID id;
    @Column("customer_id")
    private UUID customerId;
    @Column("product_id")
    private UUID productId;
    @Column("principal_amount")
    private BigDecimal principalAmount;
    @Column("outstanding_balance")
    private BigDecimal outstandingBalance;
    @Column("total_fees")
    private BigDecimal totalFees;
    @Column("loan_type")
    private LoanType loanType;
    @Column("state")
    private LoanState state;
    @Column("origination_date")
    private LocalDate originationDate;
    @Column("due_date")
    private LocalDate dueDate;
    @Column("billing_cycle_id")
    private UUID billingCycleId;
    @Column("tenure_value")
    private Integer tenureValue;
    @Column("tenure_type")
    private String tenureType;
    @Column("idempotency_key")
    private String idempotencyKey;
    @Column("saga_status")
    private String sagaStatus;
    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

}

