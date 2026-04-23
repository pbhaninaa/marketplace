package com.agrimarket.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderDashboardStatsResponse(
        LocalDate from,
        LocalDate to,
        long OrdersCount,
        BigDecimal OrdersTotal,
        long rentalBookingsCount,
        BigDecimal rentalBookingsTotal) {}


