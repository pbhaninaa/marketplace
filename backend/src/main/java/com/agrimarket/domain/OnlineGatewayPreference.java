package com.agrimarket.domain;

/**
 * Platform setting that picks the gateway behind the single online "Card / Instant EFT" option.
 * {@link #AUTO} uses PayFast when it is configured, otherwise Peach. An explicit choice is strict:
 * when the chosen gateway is not configured, online payments are unavailable (money is never routed
 * to an account the admin did not pick).
 */
public enum OnlineGatewayPreference {
    AUTO,
    PAYFAST,
    PEACH
}
