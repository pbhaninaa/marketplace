package com.agrimarket.service.live;

/**
 * "Something changed" signal pushed to open dashboards so they can reload.
 *
 * <p>Carries no business data on purpose: the browser re-fetches through the normal, permission-checked
 * endpoints. {@code providerId} scopes delivery — provider users only receive events for their own
 * organisation; platform admins and support receive everything. A {@code null} providerId is a
 * platform-level event (admins/support only).
 */
public record LiveEvent(String type, Long providerId) {

    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_STATUS = "ORDER_STATUS";
    public static final String ORDER_DELETED = "ORDER_DELETED";
    public static final String RENTAL_STATUS = "RENTAL_STATUS";
    public static final String PAYMENT = "PAYMENT";
    public static final String SUBSCRIPTION = "SUBSCRIPTION";

    public static LiveEvent of(String type, Long providerId) {
        return new LiveEvent(type, providerId);
    }
}
