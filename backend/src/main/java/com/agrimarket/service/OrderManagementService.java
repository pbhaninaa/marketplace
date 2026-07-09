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

import java.time.Instant;
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
    private final AppNotificationService appNotificationService;

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
        return updateOrderStatus(providerId, orderId, newStatus, null, null);
    }

    @Transactional
    public Order updateOrderStatus(Long providerId, Long orderId, OrderStatus newStatus, Long actorUserId) {
        return updateOrderStatus(providerId, orderId, newStatus, actorUserId, null);
    }

    /**
     * @param completedByStaffId optional payroll attribution override when marking COLLECTED
     *     (owner may re-assign completer so pay is not orphaned on the owner account).
     */
    @Transactional
    public Order updateOrderStatus(
            Long providerId, Long orderId, OrderStatus newStatus, Long actorUserId, Long completedByStaffId) {
        Order order = getOrderById(providerId, orderId);

        // Validate status transitions
        OrderStatus current = order.getStatus();
        if (current == newStatus) {
            // Allow re-attribution on already-collected orders when owner supplies a staff id
            if (current == OrderStatus.COLLECTED && completedByStaffId != null) {
                order.setCompletedByStaffId(completedByStaffId);
                if (order.getCompletedAt() == null) {
                    order.setCompletedAt(Instant.now());
                }
                return OrderRepository.save(order);
            }
            return order;
        }
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
            order.setPaymentStatus(PaymentStatus.PAID);

            // Sync rentals from same checkout session (sessionKey)
            syncRelatedRentalStatusBySessionKey(order.getSessionKey(), BookingStatus.PAID);
        } else if (current == OrderStatus.PENDING_PAYMENT && newStatus == OrderStatus.CANCELLED) {
            purchaseInventoryService.releasePendingReservation(order);
            markPurchasePaymentFailed(order);
            order.setPaymentStatus(PaymentStatus.PENDING);

            // Sync rentals from same checkout session (sessionKey)
            syncRelatedRentalStatusBySessionKey(order.getSessionKey(), BookingStatus.CANCELLED);
        } else if (current == OrderStatus.PAID && newStatus == OrderStatus.CANCELLED) {
            // Business rule (tests): do not allow cancelling PAID orders via this endpoint.
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CANCEL_PAID", "Cannot cancel paid orders");
        }

        order.setStatus(newStatus);
        if (newStatus == OrderStatus.COLLECTED) {
            if (order.getCompletedAt() == null) {
                order.setCompletedAt(Instant.now());
            }
            Long attributed = completedByStaffId != null ? completedByStaffId : actorUserId;
            if (attributed != null) {
                order.setCompletedByStaffId(attributed);
            }
        }
        Order saved = OrderRepository.save(order);
        try {
            appNotificationService.notifyOrderStatus(saved);
        } catch (Exception ignored) {
            // Never fail status update due to notifications.
        }
        return saved;
    }

    @Transactional
    public void cancelOrder(Long providerId, Long orderId) {
        Order order = getOrderById(providerId, orderId);

        if (order.getStatus() == OrderStatus.COLLECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CANCEL", "Cannot cancel collected orders");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_CANCELLED", "Order is already cancelled");
        }

        // Handle inventory restoration based on order status
        if (order.getStatus() == OrderStatus.PAID) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CANCEL_PAID", "Cannot cancel paid orders");
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            purchaseInventoryService.releasePendingReservation(order);
            markPurchasePaymentCancelled(order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        OrderRepository.save(order);
        try {
            appNotificationService.notifyOrderCancelled(order);
        } catch (Exception ignored) {
        }
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

        for (RentalBooking b : bookingsToDelete) {
            paymentRecordRepository.deleteByRentalBooking_Id(b.getId());
        }
        rentalBookingRepository.deleteAll(bookingsToDelete);
        return bookingsToDelete.size();
    }

    @Transactional
    public void deleteRental(MarketUserPrincipal user, Long rentalId) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        RentalBooking rental = rentalBookingRepository.findById(rentalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING", "Booking not found"));
        if (rental.getProvider() == null || !rental.getProvider().getId().equals(user.getProviderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROVIDER", "Not your booking");
        }

        if (rental.getStatus() != BookingStatus.CANCELLED && rental.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CANNOT_DELETE",
                    "Can only delete cancelled or pending payment bookings");
        }

        paymentRecordRepository.deleteByRentalBooking_Id(rentalId);
        rentalBookingRepository.delete(rental);
    }

    private void markPurchasePaymentCompleted(Order order) {
        paymentRecordRepository
                .findByOrder_Id(order.getId())
                .ifPresent(p -> {
                    p.setStatus(com.agrimarket.domain.PaymentRecordStatus.PAID);
                    paymentRecordRepository.save(p);
                });
    }

    private void markPurchasePaymentCancelled(Order order) {
        paymentRecordRepository
                .findByOrder_Id(order.getId())
                .ifPresent(p -> {
                    p.setStatus(com.agrimarket.domain.PaymentRecordStatus.PENDING_PAYMENT);
                    paymentRecordRepository.save(p);
                });
    }

    private void markPurchasePaymentFailed(Order order) {
        paymentRecordRepository
                .findByOrder_Id(order.getId())
                .ifPresent(p -> {
                    // A "failed" payment remains pending-payment.
                    p.setStatus(com.agrimarket.domain.PaymentRecordStatus.PENDING_PAYMENT);
                    paymentRecordRepository.save(p);
                });
    }

    /**
     * Syncs rental bookings from the same checkout session (sessionKey).
     * We cannot reuse verification codes across rentals due to DB uniqueness constraints.
     */
    private void syncRelatedRentalStatusBySessionKey(String sessionKey, BookingStatus newStatus) {
        if (sessionKey == null || sessionKey.isBlank()) return;
        for (RentalBooking rental : rentalBookingRepository.findAllBySessionKey(sessionKey)) {
            rental.setStatus(newStatus);
            rentalBookingRepository.save(rental);
            // Keep rental payment record consistent when purchase + rentals share the same checkout/payment.
            paymentRecordRepository.findByRentalBooking_Id(rental.getId())
                    .ifPresent(p -> {
                        if (newStatus == BookingStatus.PAID) {
                            p.setStatus(com.agrimarket.domain.PaymentRecordStatus.PAID);
                        } else if (newStatus == BookingStatus.CANCELLED) {
                            p.setStatus(com.agrimarket.domain.PaymentRecordStatus.PENDING_PAYMENT);
                        }
                        paymentRecordRepository.save(p);
                    });
        }
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

        // Keep payment record consistent for the rental.
        paymentRecordRepository.findByRentalBooking_Id(rental.getId())
                .ifPresent(p -> {
                    if (newStatus == BookingStatus.PAID) {
                        p.setStatus(com.agrimarket.domain.PaymentRecordStatus.PAID);
                    } else if (newStatus == BookingStatus.CANCELLED) {
                        p.setStatus(com.agrimarket.domain.PaymentRecordStatus.PENDING_PAYMENT);
                    }
                    paymentRecordRepository.save(p);
                });

        // If this rental shares a checkout/payment with a purchase (same sessionKey),
        // apply the purchase transition via the real purchase flow (inventory + payment updates).
        if (newStatus == BookingStatus.PAID || newStatus == BookingStatus.CANCELLED) {
            OrderRepository.findBySessionKey(rental.getSessionKey())
                    .filter(o -> o.getProvider() != null && o.getProvider().getId().equals(providerId))
                    .ifPresent(o -> updateOrderStatus(
                            providerId,
                            o.getId(),
                            newStatus == BookingStatus.PAID ? OrderStatus.PAID : OrderStatus.CANCELLED));
        }

        try {
            appNotificationService.notifyRentalStatus(rental);
        } catch (Exception ignored) {
        }
        return rental;
    }

    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (!OrderStatus.isValidTransition(currentStatus, newStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Cannot transition from " + currentStatus + " to " + newStatus);
        }
    }
}

