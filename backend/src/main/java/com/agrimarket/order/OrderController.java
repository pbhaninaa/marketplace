package com.agrimarket.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * PRODUCTION-GRADE ORDER CONTROLLER WITH STATE MACHINE API
 *
 * This controller exposes clean REST endpoints for order state transitions.
 * All operations are validated by the state machine and include proper error
 * handling.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OrderController {

    private final OrderService orderService;

    // ============================================================================
    // ORDER RETRIEVAL ENDPOINTS
    // ============================================================================

    /**
     * Gets a specific order by ID (provider-authorized).
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.info("Getting order {} for provider {}", orderId, providerId);
        Order order = orderService.getOrder(orderId, providerId);
        return ResponseEntity.ok(order);
    }

    /**
     * Gets all orders for a provider.
     */
    @GetMapping
    public ResponseEntity<List<Order>> getProviderOrders(
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.info("Getting all orders for provider {}", providerId);
        List<Order> orders = orderService.getProviderOrders(providerId);
        return ResponseEntity.ok(orders);
    }

    /**
     * Gets orders by status for a provider.
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Order>> getProviderOrdersByStatus(
            @PathVariable OrderStatus status,
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.info("Getting {} orders for provider {}", status, providerId);
        List<Order> orders = orderService.getProviderOrdersByStatus(providerId, status);
        return ResponseEntity.ok(orders);
    }

    // ============================================================================
    // STATE TRANSITION ENDPOINTS
    // ============================================================================

    /**
     * Verifies an order using the guest's verification code.
     * POST /api/orders/{orderId}/verify
     */
    @PostMapping("/{orderId}/verify")
    public ResponseEntity<Order> verifyOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody VerifyOrderRequest request,
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.info("Verifying order {} for provider {}", orderId, providerId);
        Order order = orderService.verifyOrder(orderId, request.getVerificationCode(), providerId);
        return ResponseEntity.ok(order);
    }

    /**
     * Confirms payment for a verified order.
     * POST /api/orders/{orderId}/confirm-payment
     */
    @PostMapping("/{orderId}/confirm-payment")
    public ResponseEntity<Order> confirmPayment(
            @PathVariable UUID orderId,
            @Valid @RequestBody ConfirmPaymentRequest request,
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.info("Confirming payment for order {} by provider {}", orderId, providerId);
        Order order = orderService.confirmPayment(
                orderId,
                request.getPaymentMethod(),
                request.getPaymentNotes(),
                providerId);
        return ResponseEntity.ok(order);
    }

    /**
     * Fulfills a paid order.
     * POST /api/orders/{orderId}/fulfill
     */
    @PostMapping("/{orderId}/fulfill")
    public ResponseEntity<Order> fulfillOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody FulfillOrderRequest request,
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.info("Fulfilling order {} by provider {}", orderId, providerId);
        Order order = orderService.fulfillOrder(orderId, request.getFulfillmentNotes(), providerId);
        return ResponseEntity.ok(order);
    }

    /**
     * Cancels an order.
     * POST /api/orders/{orderId}/cancel
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Order> cancelOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelOrderRequest request,
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.info("Cancelling order {} by user {}", orderId, providerId);
        Order order = orderService.cancelOrder(orderId, request.getCancelNotes(), providerId);
        return ResponseEntity.ok(order);
    }

    // ============================================================================
    // BULK DELETE ENDPOINTS
    // ============================================================================

    /**
     * Deletes all orders for a provider.
     * DELETE /api/orders
     */
    @DeleteMapping
    public ResponseEntity<BulkDeleteResponse> deleteAllOrders(
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.warn("Provider {} requesting deletion of ALL orders", providerId);
        int deletedCount = orderService.deleteAllProviderOrders(providerId);
        return ResponseEntity.ok(new BulkDeleteResponse(deletedCount, "All orders deleted successfully"));
    }

    /**
     * Deletes all purchase orders for a provider.
     * DELETE /api/orders/purchases
     */
    @DeleteMapping("/purchases")
    public ResponseEntity<BulkDeleteResponse> deleteAllPurchases(
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.warn("Provider {} requesting deletion of ALL purchase orders", providerId);
        int deletedCount = orderService.deleteAllProviderPurchases(providerId);
        return ResponseEntity.ok(new BulkDeleteResponse(deletedCount, "All purchase orders deleted successfully"));
    }

    /**
     * Deletes all rental orders for a provider.
     * DELETE /api/orders/rentals
     */
    @DeleteMapping("/rentals")
    public ResponseEntity<BulkDeleteResponse> deleteAllRentals(
            @RequestHeader("X-Provider-ID") UUID providerId) {

        log.warn("Provider {} requesting deletion of ALL rental orders", providerId);
        int deletedCount = orderService.deleteAllProviderRentals(providerId);
        return ResponseEntity.ok(new BulkDeleteResponse(deletedCount, "All rental orders deleted successfully"));
    }

    // ============================================================================
    // REQUEST/RESPONSE DTOs
    // ============================================================================

    /**
     * Request DTO for order verification.
     */
    public static class VerifyOrderRequest {
        @NotBlank
        @Size(min = 6, max = 6)
        private String verificationCode;

        public String getVerificationCode() {
            return verificationCode;
        }

        public void setVerificationCode(String verificationCode) {
            this.verificationCode = verificationCode;
        }
    }

    /**
     * Request DTO for payment confirmation.
     */
    public static class ConfirmPaymentRequest {
        @NotBlank
        @Size(max = 50)
        private String paymentMethod;

        @Size(max = 500)
        private String paymentNotes;

        public String getPaymentMethod() {
            return paymentMethod;
        }

        public void setPaymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
        }

        public String getPaymentNotes() {
            return paymentNotes;
        }

        public void setPaymentNotes(String paymentNotes) {
            this.paymentNotes = paymentNotes;
        }
    }

    /**
     * Request DTO for order fulfillment.
     */
    public static class FulfillOrderRequest {
        @Size(max = 500)
        private String fulfillmentNotes;

        public String getFulfillmentNotes() {
            return fulfillmentNotes;
        }

        public void setFulfillmentNotes(String fulfillmentNotes) {
            this.fulfillmentNotes = fulfillmentNotes;
        }
    }

    /**
     * Request DTO for order cancellation.
     */
    public static class CancelOrderRequest {
        @Size(max = 500)
        private String cancelNotes;

        public String getCancelNotes() {
            return cancelNotes;
        }

        public void setCancelNotes(String cancelNotes) {
            this.cancelNotes = cancelNotes;
        }
    }

    /**
     * Response DTO for bulk delete operations.
     */
    public static class BulkDeleteResponse {
        private final int deletedCount;
        private final String message;

        public BulkDeleteResponse(int deletedCount, String message) {
            this.deletedCount = deletedCount;
            this.message = message;
        }

        public int getDeletedCount() {
            return deletedCount;
        }

        public String getMessage() {
            return message;
        }
    }

    // ============================================================================
    // EXCEPTION HANDLING
    // ============================================================================

    /**
     * Handles state machine transition exceptions.
     */
    @ExceptionHandler(OrderStatus.InvalidOrderTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(OrderStatus.InvalidOrderTransitionException e) {
        log.warn("Invalid order transition attempted: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_TRANSITION", e.getMessage()));
    }

    /**
     * Handles verification code exceptions.
     */
    @ExceptionHandler({
            Order.InvalidVerificationCodeException.class,
            Order.VerificationAlreadyUsedException.class
    })
    public ResponseEntity<ErrorResponse> handleVerificationError(RuntimeException e) {
        log.warn("Verification error: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VERIFICATION_ERROR", e.getMessage()));
    }

    /**
     * Handles authorization exceptions.
     */
    @ExceptionHandler(Order.UnauthorizedProviderException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(Order.UnauthorizedProviderException e) {
        log.warn("Unauthorized access attempt: {}", e.getMessage());
        return ResponseEntity.forbidden()
                .body(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
    }

    /**
     * Handles order not found exceptions.
     */
    @ExceptionHandler(OrderService.OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderService.OrderNotFoundException e) {
        log.warn("Order not found: {}", e.getMessage());
        return ResponseEntity.notFound()
                .body(new ErrorResponse("ORDER_NOT_FOUND", e.getMessage()));
    }

    /**
     * Handles inventory exceptions.
     */
    @ExceptionHandler(OrderService.InventoryDeductionException.class)
    public ResponseEntity<ErrorResponse> handleInventoryError(OrderService.InventoryDeductionException e) {
        log.error("Inventory error: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INVENTORY_ERROR", "Failed to update inventory. Please try again."));
    }

    /**
     * Generic error response DTO.
     */
    public static class ErrorResponse {
        private final String errorCode;
        private final String message;

        public ErrorResponse(String errorCode, String message) {
            this.errorCode = errorCode;
            this.message = message;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }
    }
}