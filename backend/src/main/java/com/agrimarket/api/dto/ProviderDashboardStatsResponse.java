package com.agrimarket.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderDashboardStatsResponse(
        LocalDate from,
        LocalDate to,
        long purchaseOrdersCount,
        BigDecimal purchaseOrdersTotal,
        long rentalBookingsCount,
        BigDecimal rentalBookingsTotal) {}

