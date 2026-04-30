package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.PlatformSettings;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.SubscriptionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionUsageBilling {

    private final PlatformSettingsService platformSettingsService;
    private final OrderRepository orderRepository;
    private final SubscriptionRepository subscriptionRepository;

    public record FirstMonthInvoice(
            BigDecimal baseMonthly,
            BigDecimal usageFeePercent,
            long paidTransactionsCounted,
            BigDecimal paidOrderTotalsSum,
            BigDecimal usageFeesTotal,
            BigDecimal totalDue,
            Instant usageWindowStart) {}

    public FirstMonthInvoice computeFirstMonthInvoice(Long providerId, SubscriptionPlan plan, Instant now) {
        if (plan == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION", "Plan is required");
        }
        PlatformSettings settings = platformSettingsService.getOrCreate();
        BigDecimal base = switch (plan) {
            case BASIC -> settings.getBasicMonthly();
            case PREMIUM -> settings.getPremiumMonthly();
        };
        if (base == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION", "Subscription pricing is not configured");
        }

        Instant usageFrom = usageWindowStart(providerId, now);
        BigDecimal percent =
                settings.getUsageFeePercent() == null ? BigDecimal.ZERO : settings.getUsageFeePercent();
        long paidTx = orderRepository.countPaidTransactionsForProviderBetweenInclusive(
                providerId, PaymentStatus.PAID, usageFrom, now);
        BigDecimal paidTotals = orderRepository.sumPaidOrderTotalsForProviderBetweenInclusive(
                providerId, PaymentStatus.PAID, usageFrom, now);
        if (paidTotals == null) {
            paidTotals = BigDecimal.ZERO;
        }
        BigDecimal usageTotal = paidTotals
                .multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = base.add(usageTotal).setScale(2, RoundingMode.HALF_UP);

        return new FirstMonthInvoice(base, percent, paidTx, paidTotals, usageTotal, total, usageFrom);
    }

    /**
     * Usage is billed on a rolling monthly window aligned to the provider's current/latest subscription period
     * when possible; otherwise it falls back to the last 30 days.
     */
    private Instant usageWindowStart(Long providerId, Instant now) {
        Subscription latest = subscriptionRepository.findTopByProviderIdOrderByCreatedAtDesc(providerId).orElse(null);
        if (latest == null) {
            return now.minus(30, ChronoUnit.DAYS);
        }

        Subscription active = subscriptionRepository
                .findActiveForProviderOrderByExpiresAtDesc(providerId, SubscriptionStatus.ACTIVE, now)
                .stream()
                .findFirst()
                .orElse(null);
        if (active != null) {
            if (active.getExpiresAt() != null && active.getExpiresAt().isAfter(now)) {
                Instant anchor = active.getExpiresAt().minus(30, ChronoUnit.DAYS);
                return anchor.isBefore(Instant.EPOCH) ? Instant.EPOCH : anchor;
            }
        }

        if (latest.getExpiresAt() != null) {
            if (latest.getExpiresAt().isAfter(now)) {
                Instant anchor = latest.getExpiresAt().minus(30, ChronoUnit.DAYS);
                return anchor.isBefore(Instant.EPOCH) ? Instant.EPOCH : anchor;
            }
            Instant anchor = latest.getExpiresAt().minus(30, ChronoUnit.DAYS);
            Instant from = latest.getCreatedAt() != null && latest.getCreatedAt().isAfter(anchor) ? latest.getCreatedAt() : anchor;
            return from.isBefore(Instant.EPOCH) ? Instant.EPOCH : from;
        }

        return now.minus(30, ChronoUnit.DAYS);
    }
}
