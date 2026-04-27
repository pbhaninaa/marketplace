package com.agrimarket.service;

import com.agrimarket.domain.Order;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages payment status transitions and payment-related business logic.
 * 
 * When payment is confirmed: items are committed to inventory deduction
 * When payment is rejected/cancelled: items are released back to inventory
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final InventoryManagementService inventoryService;

    /**
     * Confirms payment for an order.
     * This triggers inventory deduction.
     */
    @Transactional
    public void confirmPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        // Deduct items from inventory
        inventoryService.deductInventoryForOrder(order);
    }

    /**
     * Cancels an order.
     * Only allowed if payment status is PENDING.
     * This releases reserved inventory items back to stock.
     */
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // Only allow cancellation if payment is PENDING
        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot cancel order with PAID payment status");
        }

        // Keep payment status as PENDING but release inventory
        inventoryService.releaseInventoryForOrder(order);
    }

    /**
     * Get current payment status of an order
     */
    public PaymentStatus getPaymentStatus(Long orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getPaymentStatus)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }
}
