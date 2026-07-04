package com.agrimarket.api.dto;

import com.agrimarket.domain.StaffRateUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StaffIncomeBundleDto(
        Long staffUserId,
        String email,
        StaffRateUnit payMethod,
        BigDecimal payRate,
        LocalDate payPeriodStart,
        LocalDate payPeriodEnd,
        BigDecimal expectedTotal,
        BigDecimal paidTotal,
        BigDecimal unpaidTotal,
        List<StaffIncomeLineDto> lines) {}
