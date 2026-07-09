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
        /** Pending unpaid amount owed to staff (Wheel Hub expectedPayment). */
        BigDecimal expectedPayment,
        BigDecimal paidPayment,
        BigDecimal unpaidPayment,
        String targetPeriod,
        BigDecimal targetValue,
        BigDecimal bonusPercentage,
        Integer unpaidTargetPeriods,
        Integer unpaidTargetMetCount) {}
