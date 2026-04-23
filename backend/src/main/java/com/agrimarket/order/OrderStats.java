package com.agrimarket.order;

import java.math.BigDecimal;

/**
 * ORDER STATISTICS DTO
 *
 * Used for provider dashboard analytics.
 */
public record OrderStats(
        long totalOrders,
        BigDecimal totalRevenue,
        BigDecimal averageOrderValue) {
}