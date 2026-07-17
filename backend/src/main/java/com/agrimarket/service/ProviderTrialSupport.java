package com.agrimarket.service;

import com.agrimarket.domain.Provider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * One-time 30-day provider free trial helpers. Trial dates are durable: once set they are never
 * reset by logins, profile edits, or re-runs of backfill.
 */
public final class ProviderTrialSupport {

    public static final int TRIAL_DAYS = 30;

    private ProviderTrialSupport() {}

    /**
     * Assigns trial start/end from {@code anchor} only when both trial fields are absent.
     * Does nothing if either field is already set (no reset / reissue).
     */
    public static void assignTrialOnce(Provider provider, Instant anchor) {
        if (provider == null) {
            return;
        }
        if (provider.getTrialStartedAt() != null || provider.getTrialEndsAt() != null) {
            return;
        }
        Instant start = anchor != null ? anchor : Instant.EPOCH;
        provider.setTrialStartedAt(start);
        provider.setTrialEndsAt(start.plus(TRIAL_DAYS, ChronoUnit.DAYS));
    }

    /** Active while {@code trialEndsAt} is strictly after {@code now} (UTC Instant). */
    public static boolean isOnTrial(Instant now, Instant trialEndsAt) {
        return trialEndsAt != null && now != null && trialEndsAt.isAfter(now);
    }

    public static boolean isOnTrial(Provider provider, Instant now) {
        return provider != null && isOnTrial(now, provider.getTrialEndsAt());
    }

    /**
     * Whole UTC calendar days from {@code now}'s UTC date to {@code trialEndsAt}'s UTC date.
     * Returns 0 when expired or missing. At the UTC midnight boundary of the end date this is 0
     * while the Instant may still be active until {@code trialEndsAt}.
     */
    public static long trialDaysRemaining(Instant now, Instant trialEndsAt) {
        if (!isOnTrial(now, trialEndsAt)) {
            return 0L;
        }
        LocalDate nowUtc = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate endUtc = LocalDate.ofInstant(trialEndsAt, ZoneOffset.UTC);
        return Math.max(0L, ChronoUnit.DAYS.between(nowUtc, endUtc));
    }
}
