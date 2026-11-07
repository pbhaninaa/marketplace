package com.agrimarket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singleton row (ID=1): referral programme settings, editable by platform admins only.
 *
 * <p>The commission rate is applied when a referred provider's first subscription is paid via
 * Peach, and the rate used is copied onto that {@link Referral}, so later changes never rewrite
 * history.
 */
@Entity
@Table(name = "referral_settings")
@Getter
@Setter
@NoArgsConstructor
public class ReferralSettings {

    public static final long SINGLETON_ID = 1L;
    public static final BigDecimal DEFAULT_COMMISSION_PERCENT = new BigDecimal("15.00");
    public static final int DEFAULT_CLAIM_WINDOW_DAYS = 30;

    @Id
    private Long id = SINGLETON_ID;

    /** Percent (0-100) of the referred provider's first paid subscription credited to the referrer. */
    @Column(name = "commission_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal commissionPercent = DEFAULT_COMMISSION_PERCENT;

    /** Days after a provider signs up during which they may still add who referred them. */
    @Column(name = "claim_window_days", nullable = false)
    private Integer claimWindowDays = DEFAULT_CLAIM_WINDOW_DAYS;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
