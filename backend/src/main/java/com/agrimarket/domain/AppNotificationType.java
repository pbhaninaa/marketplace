package com.agrimarket.domain;

/** In-app notification categories (Wheel Hub–style event types for marketplace). */
public enum AppNotificationType {
    NEW_ORDER,
    NEW_RENTAL,
    ORDER_STATUS,
    RENTAL_STATUS,
    ORDER_CANCELLED,
    SUBSCRIPTION_PROOF_PENDING,
    SUBSCRIPTION_PROOF_DECISION,
    SUBSCRIPTION_REMINDER
}
