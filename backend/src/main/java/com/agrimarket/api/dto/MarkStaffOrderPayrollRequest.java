package com.agrimarket.api.dto;

import jakarta.validation.constraints.NotNull;

public record MarkStaffOrderPayrollRequest(@NotNull Long orderId) {}
