package com.agrimarket.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminDashboardStatsResponse(
        LocalDate from,
        LocalDate to,
        long usersCreated,
        long providersCreated,
        long listingsCreated,
        long activeListings,
        long OrdersCount,
        BigDecimal OrdersTotal,
        long rentalBookingsCount,
        BigDecimal rentalBookingsTotal) {}


