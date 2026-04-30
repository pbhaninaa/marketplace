package com.agrimarket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subscription_activation_intents")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionActivationIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "amount_due", precision = 14, scale = 2, nullable = false)
    private BigDecimal amountDue;

    @Column(name = "base_monthly", precision = 14, scale = 2)
    private BigDecimal baseMonthly;

    /** Percentage applied to each paid order total in the usage window (same DB column as legacy name). */
    @Column(name = "usage_fee_per_transaction", precision = 14, scale = 2)
    private BigDecimal usageFeePercent;

    @Column(name = "paid_transactions_count", nullable = false)
    private long paidTransactionsCount;

    @Column(name = "paid_order_totals_sum", precision = 14, scale = 2)
    private BigDecimal paidOrderTotalsSum;

    @Column(name = "usage_fees_total", precision = 14, scale = 2)
    private BigDecimal usageFeesTotal;

    @Column(name = "usage_window_start", nullable = false)
    private Instant usageWindowStart;

    @Column(name = "payment_reference", length = 80, nullable = false)
    private String paymentReference;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

