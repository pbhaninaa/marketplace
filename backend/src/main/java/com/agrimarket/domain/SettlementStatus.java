package com.agrimarket.domain;

/**
 * Seller settlement state of a payment record. Only online (gateway) payments are collected by the
 * platform, so only they move past {@link #NONE}; cash and manual EFT go straight to the seller.
 */
public enum SettlementStatus {
    /** Not collected by the platform (cash / manual EFT, unpaid, or created before settlement existed). */
    NONE,
    /** Paid online; the platform holds the seller's share until it is paid out. */
    HELD,
    /** The seller's share was paid out by the platform (bank transfer reference recorded). */
    PAID_OUT,
    /** Paid online for an order / booking that had already been cancelled: the buyer must be refunded. */
    REFUND_DUE,
    /** The buyer was refunded for a {@link #REFUND_DUE} payment. */
    REFUNDED
}
