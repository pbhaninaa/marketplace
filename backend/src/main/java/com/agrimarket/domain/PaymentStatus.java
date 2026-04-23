package com.agrimarket.domain;

/**
 * Payment status for an order.
 * Tracks the payment lifecycle independently from order fulfillment status.
 */
public enum PaymentStatus {
    /**
     * Payment has been requested but not yet confirmed.
     * Inventory is RESERVED, not deducted.
     */
    PENDING,

    /**
     * Payment has been confirmed/received.
     * Inventory has been deducted.
     */
    CONFIRMED,

    /**
     * Payment was rejected or order was cancelled.
     * Inventory has been released back.
     */
    REJECTED
}
