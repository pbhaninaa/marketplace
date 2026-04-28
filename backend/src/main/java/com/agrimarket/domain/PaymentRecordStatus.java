package com.agrimarket.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Status stored in the {@code payments.status} column.
 *
 * Many existing MySQL schemas use ENUM('PENDING_PAYMENT','PAID') here, which differs from {@code orders.payment_status}.
 */
public enum PaymentRecordStatus {
    PENDING_PAYMENT,
    PAID;

    @JsonCreator
    public static PaymentRecordStatus from(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase();
        if (v.isEmpty()) return null;
        // Backwards compatibility if any client still sends "PENDING"
        if (v.equals("PENDING")) return PENDING_PAYMENT;
        return PaymentRecordStatus.valueOf(v);
    }
}

