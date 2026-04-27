package com.agrimarket.api;

import com.agrimarket.api.dto.OrderResponse;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.OrderManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Provider Order Management Controller
 *
 * Provides full CRUD operations for providers to manage their orders.
 * Enforces business rules:
 * - Orders can only be cancelled if payment status is PENDING
 * - Orders can only be deleted if payment status is PENDING
 * - Providers can only access their own orders
 */
@RestController
@RequestMapping("/api/provider/orders")
@RequiredArgsConstructor
@Slf4j
public class ProviderOrderController {

    private final OrderManagementService orderManagementService;

    /**
     * GET /api/provider/orders
     * Get all orders for the authenticated provider (paginated)
     */
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getProviderOrders(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Provider {} fetching orders - page: {}, size: {}", user.getProviderId(), page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderManagementService.getProviderOrders(user.getProviderId(), pageable);
        Page<OrderResponse> response = orders.map(OrderResponse::from);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/provider/orders/{orderId}
     * Get a specific order by ID with all line items
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long orderId) {

        log.info("Provider {} fetching order {}", user.getProviderId(), orderId);

        Order order = orderManagementService.getOrderById(user.getProviderId(), orderId);
        OrderResponse response = OrderResponse.from(order);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/provider/me/orders/purchases/{orderId}/items
     * Get all listings/items for a specific order
     */
    @GetMapping("/me/orders/purchases/{orderId}/items")
    public ResponseEntity<List<Listing>> getOrderItems(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long orderId) {

        log.info("Provider {} fetching items for order {}", user.getProviderId(), orderId);

        List<Listing> listings = orderManagementService.getListingsFromOrder(user.getProviderId(), orderId);

        return ResponseEntity.ok(listings);
    }

    /**
     * PUT /api/provider/orders/{orderId}/status
     * Update order status (verify, pay, fulfill, cancel)
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusRequest request) {

        log.info("Provider {} updating order {} to status {}",
                user.getProviderId(), orderId, request.status());

        Order order = orderManagementService.updateOrderStatus(
                user.getProviderId(), orderId, request.status());
        OrderResponse response = OrderResponse.from(order);

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/provider/orders/{orderId}/cancel
     * Cancel an order (only allowed if payment status is PENDING)
     */
    @DeleteMapping("/{orderId}/cancel")
    public ResponseEntity<Map<String, String>> cancelOrder(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long orderId) {

        log.info("Provider {} cancelling order {}", user.getProviderId(), orderId);

        orderManagementService.cancelOrder(user.getProviderId(), orderId);

        return ResponseEntity.ok(Map.of(
                "message", "Order cancelled successfully",
                "orderId", orderId.toString()));
    }

    /**
     * DELETE /api/provider/orders/{orderId}
     * Delete an order (only allowed if payment status is PENDING)
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> deleteOrder(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long orderId) {

        log.info("Provider {} deleting order {}", user.getProviderId(), orderId);

        orderManagementService.deleteOrder(user, orderId);

        return ResponseEntity.ok(Map.of(
                "message", "Order deleted successfully",
                "orderId", orderId.toString()));
    }

    /**
     * DELETE /api/provider/orders/bulk
     * Bulk delete orders (only PENDING payment orders will be deleted)
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulkDeleteOrders(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @RequestBody(required = false) BulkDeleteRequest request) {

        log.warn("Provider {} requesting bulk order deletion", user.getProviderId());

        int deletedCount;
        if (request == null || request.orderIds() == null || request.orderIds().isEmpty()) {
            deletedCount = orderManagementService.deleteAllProviderPurchases(user.getProviderId());
        } else {
            deletedCount = orderManagementService.deleteProviderPurchases(
                    user.getProviderId(), request.orderIds());
        }

        return ResponseEntity.ok(Map.of(
                "message", "Orders deleted successfully",
                "deletedCount", deletedCount));
    }

    // ============================================================================
    // RENTAL ENDPOINTS
    // ============================================================================

    /**
     * GET /api/provider/me/orders/rentals
     * Get all rental bookings for the authenticated provider (paginated)
     */
    @GetMapping("/me/orders/rentals")
    public ResponseEntity<Page<RentalBooking>> getProviderRentals(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Provider {} fetching rentals - page: {}, size: {}", user.getProviderId(), page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<RentalBooking> rentals = orderManagementService.getProviderRentals(user.getProviderId(), pageable);

        return ResponseEntity.ok(rentals);
    }

    /**
     * GET /api/provider/me/orders/rentals/{rentalId}
     * Get a specific rental booking by ID
     */
    @GetMapping("/me/orders/rentals/{rentalId}")
    public ResponseEntity<RentalBooking> getRentalById(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long rentalId) {

        log.info("Provider {} fetching rental {}", user.getProviderId(), rentalId);

        RentalBooking rental = orderManagementService.getRentalById(user.getProviderId(), rentalId);

        return ResponseEntity.ok(rental);
    }

    /**
     * PUT /api/provider/me/orders/rentals/{rentalId}/status
     * Update rental booking status
     */
    @PutMapping("/me/orders/rentals/{rentalId}/status")
    public ResponseEntity<RentalBooking> updateRentalStatus(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long rentalId,
            @RequestParam BookingStatus status) {

        log.info("Provider {} updating rental {} to status {}",
                user.getProviderId(), rentalId, status);

        RentalBooking rental = orderManagementService.updateRentalStatus(
                user.getProviderId(), rentalId, status);

        return ResponseEntity.ok(rental);
    }

    /**
     * GET /api/provider/me/orders/purchases
     * Get all purchase orders for the authenticated provider (paginated)
     */
    @GetMapping("/me/orders/purchases")
    public ResponseEntity<Page<OrderResponse>> getProviderPurchases(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Provider {} fetching purchases - page: {}, size: {}", user.getProviderId(), page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderManagementService.getProviderOrders(user.getProviderId(), pageable);
        Page<OrderResponse> response = orders.map(OrderResponse::from);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/provider/me/orders/purchases/{orderId}
     * Get a specific purchase order by ID
     */
    @GetMapping("/me/orders/purchases/{orderId}")
    public ResponseEntity<OrderResponse> getPurchaseById(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long orderId) {

        log.info("Provider {} fetching purchase {}", user.getProviderId(), orderId);

        Order order = orderManagementService.getOrderById(user.getProviderId(), orderId);
        OrderResponse response = OrderResponse.from(order);

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/provider/me/orders/purchases/{orderId}/status
     * Update purchase order status
     */
    @PutMapping("/me/orders/purchases/{orderId}/status")
    public ResponseEntity<OrderResponse> updatePurchaseStatus(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long orderId,
            @RequestParam OrderStatus status) {

        log.info("Provider {} updating purchase {} to status {}",
                user.getProviderId(), orderId, status);

        Order order = orderManagementService.updateOrderStatus(
                user.getProviderId(), orderId, status);
        OrderResponse response = OrderResponse.from(order);

        return ResponseEntity.ok(response);
    }

    // ============================================================================
    // REQUEST DTOS
    // ============================================================================

    /**
     * Request DTO for updating order status
     */
    public record UpdateOrderStatusRequest(OrderStatus status) {}

    /**
     * Request DTO for bulk delete operations
     */
    public record BulkDeleteRequest(List<Long> orderIds) {}
}
