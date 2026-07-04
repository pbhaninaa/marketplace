package com.agrimarket.api.dto;

import com.agrimarket.domain.StaffRateUnit;
import java.math.BigDecimal;

public record StaffPaymentCalculationDto(
        Long staffUserId,
        String email,
        String displayName,
        StaffRateUnit payMethod,
        BigDecimal payRate,
        long jobCount,
        BigDecimal units,
        BigDecimal expectedPayment,
        BigDecimal paidPayment,
        BigDecimal unpaidPayment,
        String targetPeriod,
        BigDecimal targetValue,
        BigDecimal bonusPercentage) {}
