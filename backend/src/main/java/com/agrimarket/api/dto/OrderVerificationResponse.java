package com.agrimarket.api.dto;

import com.agrimarket.domain.PurchaseOrder;
import java.time.Instant;

/**
 * Response DTO for order verification operations.
 * Prevents Hibernate lazy loading issues by not exposing entity relationships.
 */
public record OrderVerificationResponse(
        String message,
        Instant verifiedAt,
        Long orderId,
        String guestName,
        String guestPhone,
        String guestEmail,
        String status) {
    public static OrderVerificationResponse from(PurchaseOrder order, String message) {
        return new OrderVerificationResponse(
                message,
                order.getVerifiedAt(),
                order.getId(),
                order.getGuestName(),
                order.getGuestPhone(),
                order.getGuestEmail(),
                order.getStatus().toString());
    }
}