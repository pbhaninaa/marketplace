package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PurchaseOrder;
import com.agrimarket.repo.PurchaseOrderRepository;
import com.agrimarket.security.MarketUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for provider order management operations (CRUD)
 */
@Service
@RequiredArgsConstructor
public class OrderManagementService {

    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> getProviderOrders(Long providerId, Pageable pageable) {
        return purchaseOrderRepository.findByProvider_IdOrderByCreatedAtDesc(providerId, pageable);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getOrderById(Long providerId, Long orderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));

        if (!order.getProvider().getId().equals(providerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You don't have access to this order");
        }

        return order;
    }

    @Transactional
    public PurchaseOrder updateOrderStatus(Long providerId, Long orderId, OrderStatus newStatus) {
        PurchaseOrder order = getOrderById(providerId, orderId);

        // Validate status transitions
        validateStatusTransition(order.getStatus(), newStatus);

        order.setStatus(newStatus);
        return purchaseOrderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(Long providerId, Long orderId) {
        PurchaseOrder order = getOrderById(providerId, orderId);

        if (order.getStatus() == OrderStatus.FULFILLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CANCEL", "Cannot cancel fulfilled orders");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_CANCELLED", "Order is already cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        purchaseOrderRepository.save(order);
    }

    @Transactional
    public void deleteOrder(MarketUserPrincipal user, Long orderId) {
        // Only allow deletion of cancelled orders or pending payment
        PurchaseOrder order = getOrderById(user.getProviderId(), orderId);

        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE", 
                    "Can only delete cancelled or pending payment orders");
        }

        purchaseOrderRepository.delete(order);
    }

    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        // Define valid state transitions
        boolean isValid = switch (currentStatus) {
            case PENDING_PAYMENT -> newStatus == OrderStatus.PAID || newStatus == OrderStatus.CANCELLED;
            case PAID -> newStatus == OrderStatus.FULFILLED || newStatus == OrderStatus.CANCELLED;
            case FULFILLED -> newStatus == OrderStatus.FULFILLED; // Can only stay fulfilled
            case CANCELLED -> newStatus == OrderStatus.CANCELLED; // Can only stay cancelled
        };

        if (!isValid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Cannot transition from " + currentStatus + " to " + newStatus);
        }
    }
}
