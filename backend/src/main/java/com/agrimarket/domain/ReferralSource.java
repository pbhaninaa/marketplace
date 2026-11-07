package com.agrimarket.domain;

/** How the referral was captured. */
public enum ReferralSource {
    /** Code entered (or pre-filled from an invite link) on the provider sign-up form. */
    REGISTRATION,
    /** Code added later from the provider portal, within the admin-configured claim window. */
    CLAIM
}
