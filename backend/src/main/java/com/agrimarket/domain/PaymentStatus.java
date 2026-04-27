package com.agrimarket.domain;

/**
 * Payment status for an order.
 * Simplified to two states: PENDING and PAID
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
}
