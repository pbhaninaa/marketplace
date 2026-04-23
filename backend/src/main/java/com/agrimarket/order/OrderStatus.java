package com.agrimarket.order;

import java.util.Set;
import java.util.HashSet;

/**
 * PRODUCTION-GRADE ORDER STATUS ENUM WITH STATE MACHINE
 *
 * This enum enforces valid state transitions and prevents invalid operations.
 * All transitions are validated at the enum level to guarantee data integrity.
 */
public enum OrderStatus {

    // ============================================================================
    // STATUS DEFINITIONS
    // ============================================================================

    /**
     * Initial state: Order created but payment not confirmed.
     * Inventory is RESERVED (not deducted).
     * Provider can verify code or reject order.
     */
    PENDING_PAYMENT,

    /**
     * Provider has verified guest's meetup code.
     * Payment proof may exist but not yet confirmed.
     * Inventory still RESERVED.
     * Provider can confirm payment or reject order.
     */
    VERIFIED,

    /**
     * Payment confirmed by provider.
     * Inventory is DEDUCTED (committed).
     * Provider can fulfill or cancel (with refund).
     */
    PAID,

    /**
     * Order completed (delivered/picked up).
     * Final state - no further transitions.
     * Inventory permanently deducted.
     */
    FULFILLED,

    /**
     * Order cancelled by provider or guest.
     * Final state - no further transitions.
     * Inventory reservation released.
     */
    CANCELLED;

    // ============================================================================
    // STATE MACHINE LOGIC
    // ============================================================================

    /**
     * VALID TRANSITIONS MATRIX
     * Maps each status to its allowed next statuses.
     */
    private static final Set<Transition> VALID_TRANSITIONS = Set.of(
            // PENDING_PAYMENT transitions
            new Transition(PENDING_PAYMENT, VERIFIED),
            new Transition(PENDING_PAYMENT, CANCELLED),

            // VERIFIED transitions
            new Transition(VERIFIED, PAID),
            new Transition(VERIFIED, CANCELLED),

            // PAID transitions
            new Transition(PAID, FULFILLED),
            new Transition(PAID, CANCELLED)

    // FULFILLED and CANCELLED have no outgoing transitions (terminal states)
    );

    /**
     * Validates if a transition from current status to new status is allowed.
     *
     * @param current Current order status
     * @param target  Target order status
     * @return true if transition is valid
     */
    public static boolean isValidTransition(OrderStatus current, OrderStatus target) {
        if (current == target) {
            // Allow self-transitions only for terminal states
            return current == FULFILLED || current == CANCELLED;
        }

        return VALID_TRANSITIONS.contains(new Transition(current, target));
    }

    /**
     * Validates a transition and throws exception if invalid.
     *
     * @param current Current order status
     * @param target  Target order status
     * @throws InvalidOrderTransitionException if transition is invalid
     */
    public static void validateTransition(OrderStatus current, OrderStatus target)
            throws InvalidOrderTransitionException {

        if (!isValidTransition(current, target)) {
            throw new InvalidOrderTransitionException(
                    String.format("Invalid order status transition: %s → %s", current, target));
        }
    }

    /**
     * Gets all valid next statuses for the current status.
     *
     * @param current Current order status
     * @return Set of valid next statuses
     */
    public static Set<OrderStatus> getValidNextStatuses(OrderStatus current) {
        Set<OrderStatus> nextStatuses = new HashSet<>();

        // Add self-transition for terminal states
        if (current == FULFILLED || current == CANCELLED) {
            nextStatuses.add(current);
        }

        // Add valid transitions
        for (Transition transition : VALID_TRANSITIONS) {
            if (transition.from == current) {
                nextStatuses.add(transition.to);
            }
        }

        return nextStatuses;
    }

    /**
     * Checks if this status is a terminal state (no further transitions).
     *
     * @return true if terminal state
     */
    public boolean isTerminal() {
        return this == FULFILLED || this == CANCELLED;
    }

    /**
     * Checks if this status allows inventory deduction.
     * Only PAID and FULFILLED have deducted inventory.
     *
     * @return true if inventory should be deducted
     */
    public boolean hasDeductedInventory() {
        return this == PAID || this == FULFILLED;
    }

    /**
     * Checks if this status has reserved inventory.
     * PENDING_PAYMENT and VERIFIED have reserved inventory.
     *
     * @return true if inventory is reserved
     */
    public boolean hasReservedInventory() {
        return this == PENDING_PAYMENT || this == VERIFIED;
    }

    // ============================================================================
    // INNER CLASSES
    // ============================================================================

    /**
     * Represents a valid state transition.
     */
    private static class Transition {
        final OrderStatus from;
        final OrderStatus to;

        Transition(OrderStatus from, OrderStatus to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            Transition that = (Transition) obj;
            return from == that.from && to == that.to;
        }

        @Override
        public int hashCode() {
            return 31 * from.hashCode() + to.hashCode();
        }
    }

    /**
     * Exception thrown when an invalid state transition is attempted.
     */
    public static class InvalidOrderTransitionException extends RuntimeException {
        public InvalidOrderTransitionException(String message) {
            super(message);
        }
    }
}