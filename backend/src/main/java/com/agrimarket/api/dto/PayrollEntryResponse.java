package com.agrimarket.api.dto;

import com.agrimarket.domain.StaffRateUnit;
import java.math.BigDecimal;

public record PayrollEntryResponse(
        Long id,
        Long staffUserId,
        String staffEmail,
        BigDecimal unitsWorked,
        BigDecimal rateSnapshot,
        StaffRateUnit rateUnitSnapshot,
        BigDecimal amount,
        String notes,
        String createdAt) {}
