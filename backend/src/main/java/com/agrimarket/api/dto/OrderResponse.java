package com.agrimarket.api.dto;

import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.CartLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comprehensive order response DTO for provider dashboard.
 * Includes all order details and line items.
 */
public record OrderResponse(
        Long id,
        Long providerId,
        String guestName,
        String guestEmail,
        String guestPhone,
        String deliveryOrPickup,
        OrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        BigDecimal deliveryDistanceKm,
        BigDecimal deliveryFee,
        String verificationCode,
        Instant verifiedAt,
        Instant createdAt,
        boolean inventoryFinalized,
        List<OrderLineItem> items) {

    /**
     * Creates an OrderResponse from an Order entity.
     */
    public static OrderResponse from(Order order) {
        List<OrderLineItem> items = order.getLines().stream()
                .map(OrderLineItem::from)
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getId(),
                order.getProvider().getId(),
                order.getGuestName(),
                order.getGuestEmail(),
                order.getGuestPhone(),
                order.getDeliveryOrPickup(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getDeliveryDistanceKm(),
                order.getDeliveryFee(),
                order.getVerificationCode(),
                order.getVerifiedAt(),
                order.getCreatedAt(),
                order.isInventoryFinalized(),
                items);
    }

    /**
     * Represents a single line item in an order.
     */
    public record OrderLineItem(
            Long listingId,
            String listingTitle,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {

        public static OrderLineItem from(CartLine line) {
            BigDecimal lineTotal = line.getListing().getUnitPrice()
                    .multiply(BigDecimal.valueOf(line.getQuantity()));

            return new OrderLineItem(
                    line.getListing().getId(),
                    line.getListing().getTitle(),
                    line.getListing().getUnitPrice(),
                    line.getQuantity(),
                    lineTotal);
        }
    }
}
