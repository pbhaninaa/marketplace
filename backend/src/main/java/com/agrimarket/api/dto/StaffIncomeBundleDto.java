package com.agrimarket.api.dto;

import com.agrimarket.domain.StaffRateUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StaffIncomeBundleDto(
        Long staffUserId,
        String email,
        String displayName,
        StaffRateUnit payMethod,
        BigDecimal payRate,
        BigDecimal bonusPercentage,
        String targetPeriod,
        BigDecimal targetValue,
        LocalDate payPeriodStart,
        LocalDate payPeriodEnd,
        BigDecimal expectedTotal,
        BigDecimal paidTotal,
        BigDecimal unpaidTotal,
        boolean fixedPeriodPay,
        Integer unpaidTargetPeriods,
        Integer unpaidTargetMetCount,
        String note,
        List<StaffIncomeLineDto> lines) {}
