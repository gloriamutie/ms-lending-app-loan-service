package com.glo.lending.loan.repository.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@Table("billing_cycles")
public class BillingCycle {

    @Id
    private UUID id;

    @Column("customer_id")
    private UUID customerId;

    // for a customer who will allow consolidated billing,
    // this will be the day of month when all bills are consolidated and due
    @Column("consolidated_due_day")
    private Integer consolidatedDueDay;

    @Column("is_consolidated")
    private Boolean isConsolidated;

}

