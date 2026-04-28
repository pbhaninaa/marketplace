package com.agrimarket.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Payment status for an order.
 * Keep DB-compatible enum values.
 *
 * NOTE: Some existing MySQL schemas store this as an ENUM('PENDING','PAID').
 * To avoid runtime "Data truncated" errors, we persist PENDING/PAID while still
 * accepting "PENDING_PAYMENT" from clients.
 */
public enum PaymentStatus {
    /**
     * Payment has not been confirmed.
     * Inventory is RESERVED, not deducted.
     * Orders can be cancelled or deleted in this state.
     */
    PENDING,

    /**
     * Payment has been confirmed/received.
     * Inventory has been deducted.
     * Orders cannot be cancelled or deleted in this state.
     */
    PAID

    ;

    @JsonCreator
    public static PaymentStatus from(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase();
        if (v.isEmpty()) return null;
        // Accept newer client wording while persisting legacy DB value.
        if (v.equals("PENDING_PAYMENT")) return PENDING;
        return PaymentStatus.valueOf(v);
    }
}
