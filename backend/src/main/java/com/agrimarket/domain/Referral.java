package com.agrimarket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One referral: a provider user (referrer) invited a new provider business (referee).
 * A referred provider can have at most one referral (unique on {@code referee_provider_id}).
 */
@Entity
@Table(
        name = "referrals",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_referrals_referee_provider", columnNames = "referee_provider_id"),
        indexes = {
            @Index(name = "idx_referrals_referrer_user", columnList = "referrer_user_id"),
            @Index(name = "idx_referrals_status", columnList = "status"),
            @Index(name = "idx_referrals_created_at", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User whose referral code was used; the commission is credited to this account. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_user_id", nullable = false)
    private UserAccount referrer;

    /** Referrer's business at the time of referral (for display / self-referral checks). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_provider_id")
    private Provider referrerProvider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referee_provider_id", nullable = false)
    private Provider refereeProvider;

    /** Owner account of the referred business (or the user who claimed the referral). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referee_user_id")
    private UserAccount refereeUser;

    /** The code as entered (normalised), kept for audit. */
    @Column(name = "referral_code", length = 32, nullable = false)
    private String referralCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferralSource source = ReferralSource.REGISTRATION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferralStatus status = ReferralStatus.PENDING;

    /** Amount of the qualifying (first) subscription payment. */
    @Column(name = "payment_amount", precision = 14, scale = 2)
    private BigDecimal paymentAmount;

    /** Rate used, frozen when the commission was earned. */
    @Column(name = "commission_percent", precision = 6, scale = 2)
    private BigDecimal commissionPercent;

    @Column(name = "commission_amount", precision = 14, scale = 2)
    private BigDecimal commissionAmount;

    /** Peach merchantTransactionId of the qualifying payment (idempotency / audit). */
    @Column(name = "qualifying_payment_ref", length = 64)
    private String qualifyingPaymentRef;

    @Column(name = "qualifying_subscription_id")
    private Long qualifyingSubscriptionId;

    @Column(name = "earned_at")
    private Instant earnedAt;

    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    @Column(name = "paid_out_by", length = 255)
    private String paidOutBy;

    @Column(name = "payout_note", length = 1000)
    private String payoutNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
