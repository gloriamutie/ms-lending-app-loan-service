package com.glo.lending.loan.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating or updating a billing cycle.
 *
 * @param customerId         the customer
 * @param consolidatedDueDay day of month for consolidated due date (1-28)
 * @param isConsolidated     whether to enable consolidated billing
 */
public record BillingCycleRequest(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Consolidated due day is required")
        @Min(value = 1, message = "Due day must be between 1 and 28")
        @Max(value = 28, message = "Due day must be between 1 and 28")
        Integer consolidatedDueDay,

        @NotNull(message = "Consolidated flag is required")
        Boolean isConsolidated
) {
}

