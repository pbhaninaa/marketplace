package com.agrimarket.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PayrollEntryRequest(
        @NotNull @DecimalMin(value = "0.01", inclusive = true) @DecimalMax("100000") BigDecimal unitsWorked,
        @Size(max = 2000) String notes) {}
