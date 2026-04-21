package com.glo.lending.loan.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class BillingCycleRequest{
        @NotNull(message = "Customer ID is required")
        UUID customerId;

        @NotNull(message = "Consolidated due day is required")
        @Min(value = 1, message = "Due day must be between 1 and 28")
        @Max(value = 28, message = "Due day must be between 1 and 28")
        Integer consolidatedDueDay;

        @NotNull(message = "Consolidated flag is required")
        Boolean isConsolidated;
}

