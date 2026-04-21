package com.glo.lending.loan.repository.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Represents a consolidated billing cycle for a customer.
 * <p>
 * Allows multiple loans to share a single due date, simplifying
 * repayment schedules for customers with multiple active loans.
 * </p>
 */
@Data
@Table("billing_cycles")
public class BillingCycle {

    @Id
    private UUID id;

    @Column("customer_id")
    private UUID customerId;

    @Column("consolidated_due_day")
    private Integer consolidatedDueDay;

    @Column("is_consolidated")
    private Boolean isConsolidated;

}

