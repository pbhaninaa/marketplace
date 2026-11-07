package com.agrimarket.domain;

/** Lifecycle of a provider-to-provider referral. */
public enum ReferralStatus {
    /** Referred provider signed up; no qualifying subscription payment yet. */
    PENDING,
    /** Referred provider paid their first subscription online; commission recorded for the referrer. */
    EARNED,
    /** An admin recorded that the commission was paid out to the referrer (manual payout). */
    PAID
}
