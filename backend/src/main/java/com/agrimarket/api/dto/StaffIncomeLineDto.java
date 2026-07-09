package com.agrimarket.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StaffIncomeLineDto(
        Long orderId,
        String guestName,
        BigDecimal orderTotal,
        Instant completedAt,
        BigDecimal units,
        BigDecimal lineAmount,
        boolean payrollPaid,
        String note) {}
