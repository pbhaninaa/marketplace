package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.CartLineRepository;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.security.MarketUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Service for provider order management operations (CRUD)
 */
@Service
@RequiredArgsConstructor
public class OrderManagementService {

    private final OrderRepository OrderRepository;
    private final PurchaseInventoryService purchaseInventoryService;
    private final PaymentRecordRepository paymentRecordRepository;
    private final RentalBookingRepository rentalBookingRepository;
    private final CartLineRepository cartLineRepository;

    @Transactional(readOnly = true)
    public Page<Order> getProviderOrders(Long providerId, Pageable pageable) {
        return OrderRepository.findByProvider_IdOrderByCreatedAtDesc(providerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<RentalBooking> getProviderRentals(Long providerId, Pageable pageable) {
        return rentalBookingRepository.findByProvider_IdOrderByCreatedAtDesc(providerId, pageable);
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long providerId, Long orderId) {
        Order order = OrderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));

        if (!order.getProvider().getId().equals(providerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You don't have access to this order");
        }

        return order;
    }

    @Transactional(readOnly = true)
    public RentalBooking getRentalById(Long providerId, Long rentalId) {
        RentalBooking rental = rentalBookingRepository.findById(rentalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RENTAL_NOT_FOUND", "Rental booking not found"));

        if (!rental.getProvider().getId().equals(providerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You don't have access to this rental");
        }

        return rental;
    }

    @Transactional(readOnly = true)
    public List<Listing> getListingsFromOrder(Long providerId, Long orderId) {
        // Verify provider has access to this order
        getOrderById(providerId, orderId);

        return cartLineRepository.findByOrderId(orderId)
                .stream()
                .map(CartLine::getListing)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public Order updateOrderStatus(Long providerId, Long orderId, OrderStatus newStatus) {
        Order order = getOrderById(providerId, orderId);

        // Validate status transitions
        OrderStatus current = order.getStatus();
        validateStatusTransition(current, newStatus);

        if (current == OrderStatus.PENDING_PAYMENT && newStatus == OrderStatus.PAID) {
            // Temporarily skip meetup code verification so providers can progress order
            // statuses
            // during testing/demos. Re-enable this check once the verification flow is back
            // in scope.
            // if (order.getVerifiedAt() == null) {
            // throw new ApiException(
            // HttpStatus.BAD_REQUEST,
            // "VERIFY_CODE_REQUIRED",
            // "Verify the guest's meetup code before confirming payment.");
            // }
            purchaseInventoryService.finalizePaidPurchase(order);
            markPurchasePaymentCompleted(order);

            // Sync related rental with same verification code
            syncRelatedRentalStatus(order.getVerificationCode(), BookingStatus.CONFIRMED);
        } else if (current == OrderStatus.PENDING_PAYMENT && newStatus == OrderStatus.CANCELLED) {
            purchaseInventoryService.releasePendingReservation(order);
            markPurchasePaymentFailed(order);

            // Sync related rental with same verification code
            syncRelatedRentalStatus(order.getVerificationCode(), BookingStatus.CANCELLED);
        } else if (current == OrderStatus.PAID && newStatus == OrderStatus.CANCELLED) {
            // Restore inventory when cancelling a PAID order
            purchaseInventoryService.restoreDeductedInventory(order);
            markPurchasePaymentFailed(order);

            // Sync related rental with same verification code
            syncRelatedRentalStatus(order.getVerificationCode(), BookingStatus.CANCELLED);
        }

        order.setStatus(newStatus);
        return OrderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(Long providerId, Long orderId) {
        Order order = getOrderById(providerId, orderId);

        if (order.getStatus() == OrderStatus.FULFILLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CANCEL", "Cannot cancel fulfilled orders");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_CANCELLED", "Order is already cancelled");
        }

        // Handle inventory restoration based on order status
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            purchaseInventoryService.releasePendingReservation(order);
            markPurchasePaymentCancelled(order);
        } else if (order.getStatus() == OrderStatus.PAID) {
            // Restore deducted inventory for PAID orders
            purchaseInventoryService.restoreDeductedInventory(order);
            markPurchasePaymentCancelled(order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        OrderRepository.save(order);
    }

    @Transactional
    public void deleteOrder(MarketUserPrincipal user, Long orderId) {
        Order order = getOrderById(user.getProviderId(), orderId);

        // Only allow deletion if payment is PENDING
        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_PAID",
                    "Can only delete orders with PENDING payment status");
        }

        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE",
                    "Can only delete cancelled or pending payment orders");
        }

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && !order.isInventoryFinalized()) {
            purchaseInventoryService.releasePendingReservation(order);
        }

        // Delete related payment records first to avoid foreign key constraint violations
        paymentRecordRepository.deleteByOrder_Id(orderId);
        OrderRepository.delete(order);
    }

    @Transactional
    public int deleteAllProviderPurchases(Long providerId) {
        // Only delete orders with PENDING payment status
        List<Order> ordersToDelete = OrderRepository
                .findByProvider_IdAndStatusIn(providerId,
                        List.of(OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT));

        // Filter to only include PENDING payment
        ordersToDelete = ordersToDelete.stream()
                .filter(order -> order.getPaymentStatus() == PaymentStatus.PENDING)
                .toList();

        for (Order order : ordersToDelete) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT && !order.isInventoryFinalized()) {
                purchaseInventoryService.releasePendingReservation(order);
            }
            // Delete related payment records first to avoid foreign key constraint violations
            paymentRecordRepository.deleteByOrder_Id(order.getId());
        }

        OrderRepository.deleteAll(ordersToDelete);
        return ordersToDelete.size();
    }

    @Transactional
    public int deleteProviderPurchases(Long providerId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return deleteAllProviderPurchases(providerId);
        }

        int deletedCount = 0;
        for (Long orderId : ids) {
            Order order = OrderRepository.findById(orderId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));

            if (!order.getProvider().getId().equals(providerId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You don't have access to this order");
            }

            // Only allow deletion if payment is PENDING
            if (order.getPaymentStatus() != PaymentStatus.PENDING) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_PAID",
                        "Can only delete orders with PENDING payment status");
            }

            if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE",
                        "Can only delete cancelled or pending payment orders");
            }

            if (order.getStatus() == OrderStatus.PENDING_PAYMENT && !order.isInventoryFinalized()) {
                purchaseInventoryService.releasePendingReservation(order);
            }

            // Delete related payment records first to avoid foreign key constraint violations
            paymentRecordRepository.deleteByOrder_Id(orderId);
            OrderRepository.delete(order);
            deletedCount++;
        }

        return deletedCount;
    }

    @Transactional
    public int deleteAllProviderRentals(Long providerId) {
        // Only delete cancelled or pending payment bookings
        List<RentalBooking> bookingsToDelete = rentalBookingRepository
                .findByProvider_IdAndStatusIn(providerId,
                        List.of(BookingStatus.CANCELLED, BookingStatus.PENDING_PAYMENT));

        rentalBookingRepository.deleteAll(bookingsToDelete);
        return bookingsToDelete.size();
    }

    private void markPurchasePaymentCompleted(Order order) {
        paymentRecordRepository
                .findByOrder_Id(order.getId())
                .ifPresent(p -> {
                    p.setStatus(PaymentStatus.PAID);
                    paymentRecordRepository.save(p);
                });
    }

    private void markPurchasePaymentCancelled(Order order) {
        paymentRecordRepository
                .findByOrder_Id(order.getId())
                .ifPresent(p -> {
                    p.setStatus(PaymentStatus.PENDING);
                    paymentRecordRepository.save(p);
                });
    }

    private void markPurchasePaymentFailed(Order order) {
        paymentRecordRepository
                .findByOrder_Id(order.getId())
                .ifPresent(p -> {
                    p.setStatus(PaymentStatus.FAILED);
                    paymentRecordRepository.save(p);
                });
    }

    /**
     * Syncs the status of any related rental booking that shares the same verification code.
     * This is used when purchase and rental are done together in the same checkout session.
     */
    private void syncRelatedRentalStatus(String verificationCode, BookingStatus newStatus) {
        rentalBookingRepository.findByVerificationCode(verificationCode)
                .ifPresent(rental -> {
                    rental.setStatus(newStatus);
                    rentalBookingRepository.save(rental);
                });
    }

    /**
     * Syncs the status of any related purchase order that shares the same verification code.
     * This is used when purchase and rental are done together in the same checkout session.
     */
    private void syncRelatedPurchaseStatus(String verificationCode, OrderStatus newStatus) {
        OrderRepository.findByVerificationCode(verificationCode)
                .ifPresent(order -> {
                    order.setStatus(newStatus);
                    OrderRepository.save(order);
                });
    }

    /**
     * Updates rental booking status and syncs related purchase if they share verification code.
     */
    @Transactional
    public RentalBooking updateRentalStatus(Long providerId, Long rentalId, BookingStatus newStatus) {
        // Find rental and verify provider ownership
        RentalBooking rental = rentalBookingRepository.findById(rentalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RENTAL_NOT_FOUND", "Rental booking not found"));

        if (!rental.getProvider().getId().equals(providerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You don't have access to this rental");
        }

        // Update rental status
        rental.setStatus(newStatus);
        rentalBookingRepository.save(rental);

        // Sync related purchase order if it exists
        if (newStatus == BookingStatus.CONFIRMED) {
            syncRelatedPurchaseStatus(rental.getVerificationCode(), OrderStatus.PAID);
        } else if (newStatus == BookingStatus.CANCELLED) {
            syncRelatedPurchaseStatus(rental.getVerificationCode(), OrderStatus.CANCELLED);
        }

        return rental;
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

