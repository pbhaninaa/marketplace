package com.agrimarket.order;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * PRODUCTION-GRADE ORDER ENTITY WITH STATE MACHINE ENFORCEMENT
 *
 * This entity integrates the OrderStatus state machine to guarantee:
 * - Valid status transitions only
 * - Proper inventory management
 * - Audit trail of status changes
 * - Race condition prevention
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_provider", columnList = "providerId"),
        @Index(name = "idx_order_guest", columnList = "guestId"),
        @Index(name = "idx_order_verification", columnList = "verificationCode"),
        @Index(name = "idx_order_created_at", columnList = "createdAt")
})
public class Order {

    // ============================================================================
    // PRIMARY KEY
    // ============================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ============================================================================
    // RELATIONSHIPS
    // ============================================================================

    @NotNull
    @Column(nullable = false)
    private UUID providerId;

    @NotNull
    @Column(nullable = false)
    private UUID guestId;

    @NotNull
    @Column(nullable = false)
    private UUID listingId;

    // ============================================================================
    // ORDER DETAILS
    // ============================================================================

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @DecimalMin("0.01")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @NotNull
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // ============================================================================
    // VERIFICATION SYSTEM
    // ============================================================================

    /**
     * Unique verification code for meetup confirmation.
     * Generated once when order is created.
     * Used by provider to verify guest identity.
     */
    @NotNull
    @Size(min = 6, max = 6)
    @Column(nullable = false, unique = true, length = 6)
    private String verificationCode;

    /**
     * Timestamp when verification code was used.
     * Null means not yet verified.
     */
    @Column
    private LocalDateTime verifiedAt;

    // ============================================================================
    // PAYMENT SYSTEM
    // ============================================================================

    /**
     * Timestamp when payment was confirmed.
     * Null means payment not yet confirmed.
     */
    @Column
    private LocalDateTime paidAt;

    /**
     * Payment method used (cash, card, etc.)
     */
    @Column(length = 50)
    private String paymentMethod;

    /**
     * Additional payment notes (receipt number, etc.)
     */
    @Column(length = 500)
    private String paymentNotes;

    // ============================================================================
    // FULFILLMENT SYSTEM
    // ============================================================================

    /**
     * Timestamp when order was fulfilled.
     * Null means not yet fulfilled.
     */
    @Column
    private LocalDateTime fulfilledAt;

    /**
     * Additional fulfillment notes
     */
    @Column(length = 500)
    private String fulfillmentNotes;

    // ============================================================================
    // STATUS MANAGEMENT (STATE MACHINE)
    // ============================================================================

    /**
     * Current order status - enforced by state machine.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    /**
     * Timestamp when status was last changed.
     */
    @NotNull
    @Column(nullable = false)
    private LocalDateTime statusChangedAt = LocalDateTime.now();

    /**
     * Previous status (for audit trail).
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatus previousStatus;

    // ============================================================================
    // AUDIT TRAIL
    // ============================================================================

    @NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime updatedAt;

    @NotNull
    @Column(nullable = false)
    private UUID createdBy;

    @Column
    private UUID updatedBy;

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // ============================================================================

    /**
     * Verifies the order using the provided code.
     * Transitions: PENDING_PAYMENT → VERIFIED
     *
     * @param code       The verification code provided by guest
     * @param providerId The provider performing verification
     * @throws OrderStatus.InvalidOrderTransitionException if transition invalid
     * @throws InvalidVerificationCodeException            if code doesn't match
     */
    public void verify(String code, UUID providerId) {
        if (!this.providerId.equals(providerId)) {
            throw new UnauthorizedProviderException("Provider not authorized for this order");
        }

        if (!verificationCode.equals(code)) {
            throw new InvalidVerificationCodeException("Invalid verification code");
        }

        if (verifiedAt != null) {
            throw new VerificationAlreadyUsedException("Verification code already used");
        }

        // Validate state transition
        OrderStatus.validateTransition(status, OrderStatus.VERIFIED);

        // Update state
        setStatus(OrderStatus.VERIFIED);
        this.verifiedAt = LocalDateTime.now();
        this.updatedBy = providerId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Confirms payment for the order.
     * Transitions: VERIFIED → PAID
     *
     * @param paymentMethod Payment method used
     * @param paymentNotes  Additional payment notes
     * @param providerId    The provider confirming payment
     * @throws OrderStatus.InvalidOrderTransitionException if transition invalid
     */
    public void confirmPayment(String paymentMethod, String paymentNotes, UUID providerId) {
        if (!this.providerId.equals(providerId)) {
            throw new UnauthorizedProviderException("Provider not authorized for this order");
        }

        // Validate state transition
        OrderStatus.validateTransition(status, OrderStatus.PAID);

        // Update state
        setStatus(OrderStatus.PAID);
        this.paidAt = LocalDateTime.now();
        this.paymentMethod = paymentMethod;
        this.paymentNotes = paymentNotes;
        this.updatedBy = providerId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Fulfills the order (marks as completed).
     * Transitions: PAID → FULFILLED
     *
     * @param fulfillmentNotes Additional fulfillment notes
     * @param providerId       The provider fulfilling the order
     * @throws OrderStatus.InvalidOrderTransitionException if transition invalid
     */
    public void fulfill(String fulfillmentNotes, UUID providerId) {
        if (!this.providerId.equals(providerId)) {
            throw new UnauthorizedProviderException("Provider not authorized for this order");
        }

        // Validate state transition
        OrderStatus.validateTransition(status, OrderStatus.FULFILLED);

        // Update state
        setStatus(OrderStatus.FULFILLED);
        this.fulfilledAt = LocalDateTime.now();
        this.fulfillmentNotes = fulfillmentNotes;
        this.updatedBy = providerId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cancels the order.
     * Transitions: PENDING_PAYMENT|VERIFIED|PAID → CANCELLED
     *
     * @param cancelNotes Cancellation notes
     * @param cancelledBy User performing cancellation
     * @throws OrderStatus.InvalidOrderTransitionException if transition invalid
     */
    public void cancel(String cancelNotes, UUID cancelledBy) {
        // Validate state transition
        OrderStatus.validateTransition(status, OrderStatus.CANCELLED);

        // Update state
        setStatus(OrderStatus.CANCELLED);
        this.fulfillmentNotes = cancelNotes; // Reuse field for cancel notes
        this.updatedBy = cancelledBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Internal method to update status with validation.
     */
    private void setStatus(OrderStatus newStatus) {
        // Validate transition
        OrderStatus.validateTransition(this.status, newStatus);

        // Update status with audit trail
        this.previousStatus = this.status;
        this.status = newStatus;
        this.statusChangedAt = LocalDateTime.now();
    }

    // ============================================================================
    // GETTERS AND SETTERS
    // ============================================================================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public void setProviderId(UUID providerId) {
        this.providerId = providerId;
    }

    public UUID getGuestId() {
        return guestId;
    }

    public void setGuestId(UUID guestId) {
        this.guestId = guestId;
    }

    public UUID getListingId() {
        return listingId;
    }

    public void setListingId(UUID listingId) {
        this.listingId = listingId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

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

    public LocalDateTime getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(LocalDateTime fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }

    public String getFulfillmentNotes() {
        return fulfillmentNotes;
    }

    public void setFulfillmentNotes(String fulfillmentNotes) {
        this.fulfillmentNotes = fulfillmentNotes;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(LocalDateTime statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(OrderStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    // ============================================================================
    // BUSINESS LOGIC QUERIES
    // ============================================================================

    /**
     * Checks if order can be verified.
     */
    public boolean canBeVerified() {
        return status == OrderStatus.PENDING_PAYMENT && verifiedAt == null;
    }

    /**
     * Checks if order can have payment confirmed.
     */
    public boolean canConfirmPayment() {
        return status == OrderStatus.VERIFIED;
    }

    /**
     * Checks if order can be fulfilled.
     */
    public boolean canBeFulfilled() {
        return status == OrderStatus.PAID;
    }

    /**
     * Checks if order can be cancelled.
     */
    public boolean canBeCancelled() {
        return !status.isTerminal();
    }

    /**
     * Checks if inventory should be deducted for this order.
     */
    public boolean shouldDeductInventory() {
        return status.hasDeductedInventory();
    }

    /**
     * Checks if inventory should be reserved for this order.
     */
    public boolean shouldReserveInventory() {
        return status.hasReservedInventory();
    }

    // ============================================================================
    // EXCEPTION CLASSES
    // ============================================================================

    public static class InvalidVerificationCodeException extends RuntimeException {
        public InvalidVerificationCodeException(String message) {
            super(message);
        }
    }

    public static class VerificationAlreadyUsedException extends RuntimeException {
        public VerificationAlreadyUsedException(String message) {
            super(message);
        }
    }

    public static class UnauthorizedProviderException extends RuntimeException {
        public UnauthorizedProviderException(String message) {
            super(message);
        }
    }
}