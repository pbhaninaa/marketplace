package com.agrimarket.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.assertj.core.api.Assertions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * COMPREHENSIVE ORDER STATE MACHINE TESTS
 *
 * Tests all valid and invalid state transitions to ensure the state machine
 * prevents invalid operations and allows only correct flows.
 */
@DisplayName("Order State Machine Tests")
class OrderStateMachineTest {

    private Order order;
    private UUID providerId;
    private UUID guestId;
    private UUID listingId;

    @BeforeEach
    void setUp() {
        providerId = UUID.randomUUID();
        guestId = UUID.randomUUID();
        listingId = UUID.randomUUID();

        order = new Order();
        order.setId(UUID.randomUUID());
        order.setProviderId(providerId);
        order.setGuestId(guestId);
        order.setListingId(listingId);
        order.setQuantity(2);
        order.setUnitPrice(new BigDecimal("25.00"));
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setVerificationCode("ABC123");
        order.setCreatedBy(guestId);
    }

    @Nested
    @DisplayName("Initial State: PENDING_PAYMENT")
    class PendingPaymentStateTests {

        @Test
        @DisplayName("Should allow verification with correct code")
        void shouldAllowVerificationWithCorrectCode() {
            // When
            order.verify("ABC123", providerId);

            // Then
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.VERIFIED);
            Assertions.assertThat(order.getVerifiedAt()).isNotNull();
            Assertions.assertThat(order.getPreviousStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("Should reject verification with wrong code")
        void shouldRejectVerificationWithWrongCode() {
            // When/Then
            Assertions.assertThatThrownBy(() -> order.verify("WRONG", providerId))
                    .isInstanceOf(Order.InvalidVerificationCodeException.class)
                    .hasMessage("Invalid verification code");
        }

        @Test
        @DisplayName("Should reject verification by wrong provider")
        void shouldRejectVerificationByWrongProvider() {
            // When/Then
            Assertions.assertThatThrownBy(() -> order.verify("ABC123", UUID.randomUUID()))
                    .isInstanceOf(Order.UnauthorizedProviderException.class)
                    .hasMessage("Provider not authorized for this order");
        }

        @Test
        @DisplayName("Should allow cancellation")
        void shouldAllowCancellation() {
            // When
            order.cancel("Guest didn't show up", providerId);

            // Then
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            Assertions.assertThat(order.getPreviousStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("Should reject direct payment confirmation")
        void shouldRejectDirectPaymentConfirmation() {
            // When/Then
            Assertions.assertThatThrownBy(() -> order.confirmPayment("cash", "test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class)
                    .hasMessageContaining("PENDING_PAYMENT → PAID");
        }

        @Test
        @DisplayName("Should reject direct fulfillment")
        void shouldRejectDirectFulfillment() {
            // When/Then
            Assertions.assertThatThrownBy(() -> order.fulfill("test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class)
                    .hasMessageContaining("PENDING_PAYMENT → FULFILLED");
        }
    }

    @Nested
    @DisplayName("Verified State: VERIFIED")
    class VerifiedStateTests {

        @BeforeEach
        void setUpVerified() {
            order.verify("ABC123", providerId);
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.VERIFIED);
        }

        @Test
        @DisplayName("Should allow payment confirmation")
        void shouldAllowPaymentConfirmation() {
            // When
            order.confirmPayment("cash", "Paid in person", providerId);

            // Then
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            Assertions.assertThat(order.getPaidAt()).isNotNull();
            Assertions.assertThat(order.getPaymentMethod()).isEqualTo("cash");
            Assertions.assertThat(order.getPreviousStatus()).isEqualTo(OrderStatus.VERIFIED);
        }

        @Test
        @DisplayName("Should allow cancellation")
        void shouldAllowCancellation() {
            // When
            order.cancel("Payment disputed", providerId);

            // Then
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            Assertions.assertThat(order.getPreviousStatus()).isEqualTo(OrderStatus.VERIFIED);
        }

        @Test
        @DisplayName("Should reject duplicate verification")
        void shouldRejectDuplicateVerification() {
            // When/Then
            Assertions.assertThatThrownBy(() -> order.verify("ABC123", providerId))
                    .isInstanceOf(Order.VerificationAlreadyUsedException.class)
                    .hasMessage("Verification code already used");
        }

        @Test
        @DisplayName("Should reject direct fulfillment")
        void shouldRejectDirectFulfillment() {
            // When/Then
            Assertions.assertThatThrownBy(() -> order.fulfill("test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class)
                    .hasMessageContaining("VERIFIED → FULFILLED");
        }
    }

    @Nested
    @DisplayName("Paid State: PAID")
    class PaidStateTests {

        @BeforeEach
        void setUpPaid() {
            order.verify("ABC123", providerId);
            order.confirmPayment("card", "Paid via card", providerId);
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("Should allow fulfillment")
        void shouldAllowFulfillment() {
            // When
            order.fulfill("Delivered successfully", providerId);

            // Then
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);
            Assertions.assertThat(order.getFulfilledAt()).isNotNull();
            Assertions.assertThat(order.getPreviousStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("Should allow cancellation")
        void shouldAllowCancellation() {
            // When
            order.cancel("Customer returned items", providerId);

            // Then
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            Assertions.assertThat(order.getPreviousStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("Should reject verification")
        void shouldRejectVerification() {
            // When/Then
            Assertions.assertThatThrownBy(() -> order.verify("ABC123", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class)
                    .hasMessageContaining("PAID → VERIFIED");
        }

        @Test
        @DisplayName("Should reject payment confirmation")
        void shouldRejectPaymentConfirmation() {
            // When/Then
            Assertions.assertThatThrownBy(() -> order.confirmPayment("cash", "test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class)
                    .hasMessageContaining("PAID → PAID");
        }
    }

    @Nested
    @DisplayName("Terminal States: FULFILLED and CANCELLED")
    class TerminalStateTests {

        @Test
        @DisplayName("Should reject all transitions from FULFILLED")
        void shouldRejectAllTransitionsFromFulfilled() {
            // Setup
            order.verify("ABC123", providerId);
            order.confirmPayment("cash", "test", providerId);
            order.fulfill("test", providerId);
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);

            // Test all invalid transitions
            Assertions.assertThatThrownBy(() -> order.verify("ABC123", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class);

            Assertions.assertThatThrownBy(() -> order.confirmPayment("cash", "test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class);

            Assertions.assertThatThrownBy(() -> order.fulfill("test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class);

            Assertions.assertThatThrownBy(() -> order.cancel("test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class);
        }

        @Test
        @DisplayName("Should reject all transitions from CANCELLED")
        void shouldRejectAllTransitionsFromCancelled() {
            // Setup
            order.cancel("test", providerId);
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

            // Test all invalid transitions
            Assertions.assertThatThrownBy(() -> order.verify("ABC123", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class);

            Assertions.assertThatThrownBy(() -> order.confirmPayment("cash", "test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class);

            Assertions.assertThatThrownBy(() -> order.fulfill("test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class);

            Assertions.assertThatThrownBy(() -> order.cancel("test", providerId))
                    .isInstanceOf(OrderStatus.InvalidOrderTransitionException.class);
        }

        @Test
        @DisplayName("Should allow self-transition for terminal states")
        void shouldAllowSelfTransitionForTerminalStates() {
            // FULFILLED allows self-transition
            order.verify("ABC123", providerId);
            order.confirmPayment("cash", "test", providerId);
            order.fulfill("test", providerId);

            // Should not throw exception
            Assertions.assertThat(order.getStatus()).isEqualTo(OrderStatus.FULFILLED);

            // CANCELLED allows self-transition
            Order cancelledOrder = new Order();
            cancelledOrder.setId(UUID.randomUUID());
            cancelledOrder.setProviderId(providerId);
            cancelledOrder.setVerificationCode("XYZ789");
            cancelledOrder.cancel("test", providerId);

            Assertions.assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("Order Status Enum Validation")
    class OrderStatusEnumTests {

        @Test
        @DisplayName("Should validate valid transitions")
        void shouldValidateValidTransitions() {
            // Valid transitions should not throw
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.VERIFIED))
                    .isTrue();
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.VERIFIED, OrderStatus.PAID)).isTrue();
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.PAID, OrderStatus.FULFILLED)).isTrue();
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED))
                    .isTrue();
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.VERIFIED, OrderStatus.CANCELLED)).isTrue();
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("Should reject invalid transitions")
        void shouldRejectInvalidTransitions() {
            // Invalid transitions should be rejected
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID))
                    .isFalse();
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.FULFILLED))
                    .isFalse();
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.VERIFIED, OrderStatus.FULFILLED)).isFalse();
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.FULFILLED, OrderStatus.CANCELLED))
                    .isFalse();
            Assertions.assertThat(OrderStatus.isValidTransition(OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT))
                    .isFalse();
        }

        @Test
        @DisplayName("Should identify terminal states correctly")
        void shouldIdentifyTerminalStatesCorrectly() {
            Assertions.assertThat(OrderStatus.FULFILLED.isTerminal()).isTrue();
            Assertions.assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
            Assertions.assertThat(OrderStatus.PENDING_PAYMENT.isTerminal()).isFalse();
            Assertions.assertThat(OrderStatus.VERIFIED.isTerminal()).isFalse();
            Assertions.assertThat(OrderStatus.PAID.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("Should identify inventory states correctly")
        void shouldIdentifyInventoryStatesCorrectly() {
            // Reserved inventory states
            Assertions.assertThat(OrderStatus.PENDING_PAYMENT.hasReservedInventory()).isTrue();
            Assertions.assertThat(OrderStatus.VERIFIED.hasReservedInventory()).isTrue();

            // Deducted inventory states
            Assertions.assertThat(OrderStatus.PAID.hasDeductedInventory()).isTrue();
            Assertions.assertThat(OrderStatus.FULFILLED.hasDeductedInventory()).isTrue();

            // No inventory impact
            Assertions.assertThat(OrderStatus.CANCELLED.hasReservedInventory()).isFalse();
            Assertions.assertThat(OrderStatus.CANCELLED.hasDeductedInventory()).isFalse();
        }
    }

    @Nested
    @DisplayName("Business Logic Queries")
    class BusinessLogicTests {

        @Test
        @DisplayName("Should correctly identify actionable states")
        void shouldCorrectlyIdentifyActionableStates() {
            // PENDING_PAYMENT state
            Assertions.assertThat(order.canBeVerified()).isTrue();
            Assertions.assertThat(order.canConfirmPayment()).isFalse();
            Assertions.assertThat(order.canBeFulfilled()).isFalse();
            Assertions.assertThat(order.canBeCancelled()).isTrue();

            // VERIFIED state
            order.verify("ABC123", providerId);
            Assertions.assertThat(order.canBeVerified()).isFalse();
            Assertions.assertThat(order.canConfirmPayment()).isTrue();
            Assertions.assertThat(order.canBeFulfilled()).isFalse();
            Assertions.assertThat(order.canBeCancelled()).isTrue();

            // PAID state
            order.confirmPayment("cash", "test", providerId);
            Assertions.assertThat(order.canBeVerified()).isFalse();
            Assertions.assertThat(order.canConfirmPayment()).isFalse();
            Assertions.assertThat(order.canBeFulfilled()).isTrue();
            Assertions.assertThat(order.canBeCancelled()).isTrue();

            // FULFILLED state (terminal)
            order.fulfill("test", providerId);
            Assertions.assertThat(order.canBeVerified()).isFalse();
            Assertions.assertThat(order.canConfirmPayment()).isFalse();
            Assertions.assertThat(order.canBeFulfilled()).isFalse();
            Assertions.assertThat(order.canBeCancelled()).isFalse();
        }
    }
}