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

        order.setPaymentStatus(PaymentStatus.CONFIRMED);
        orderRepository.save(order);

        // Deduct items from inventory
        inventoryService.deductInventoryForOrder(order);
    }

    /**
     * Rejects payment for an order.
     * This releases reserved inventory items back to stock.
     */
    @Transactional
    public void rejectPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.setPaymentStatus(PaymentStatus.REJECTED);
        orderRepository.save(order);

        // Release inventory items back
        inventoryService.releaseInventoryForOrder(order);
    }

    /**
     * Cancels an order.
     * If payment was confirmed, inventory is released.
     */
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.setPaymentStatus(PaymentStatus.REJECTED);
        orderRepository.save(order);

        // If payment was already confirmed, release inventory
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
