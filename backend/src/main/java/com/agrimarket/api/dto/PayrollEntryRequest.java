package com.agrimarket.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PayrollEntryRequest(
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal unitsWorked, String notes) {}
