package com.agrimarket.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionQuoteResponse(
        Long intentId,
        BigDecimal amountDue,
        String paymentReference,
        BigDecimal baseMonthly,
        BigDecimal usageFeePercent,
        long paidTransactionsCounted,
        BigDecimal paidOrderTotalsSum,
        BigDecimal usageFeesTotal,
        Instant usageWindowStart) {}

