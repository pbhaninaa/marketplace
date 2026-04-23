// ============================================================================
// PRODUCTION-GRADE ORDER STATE MACHINE DESIGN
// ============================================================================

/**
 * ORDER STATUS STATE MACHINE
 * ==========================
 *
 * This design guarantees data integrity, prevents invalid transitions,
 * and handles real marketplace risks (fraud, stock inconsistency, race
 * conditions).
 *
 * STATUS DEFINITIONS:
 * ===================
 *
 * PENDING_PAYMENT
 * - Initial state after checkout
 * - Guest has not yet paid or payment not confirmed
 * - Inventory is RESERVED (not deducted)
 * - Provider can: verify code, reject order
 * - Cannot: confirm payment, fulfill
 *
 * VERIFIED
 * - Provider has verified guest's meetup code
 * - Payment proof may be present but not yet confirmed
 * - Inventory still RESERVED
 * - Provider can: confirm payment, reject order
 * - Cannot: fulfill (must be PAID first)
 *
 * PAID
 * - Payment confirmed by provider
 * - Inventory is DEDUCTED (committed)
 * - Provider can: fulfill order, cancel (with refund)
 * - Cannot: reject (payment already confirmed)
 *
 * FULFILLED
 * - Order completed (delivered/picked up)
 * - Final state - no further transitions
 * - Inventory permanently deducted
 * - Provider can: nothing (terminal state)
 *
 * CANCELLED
 * - Order cancelled by provider or guest
 * - Inventory reservation RELEASED
 * - Final state - no further transitions
 * - Provider can: delete order (cleanup)
 *
 * VALID TRANSITIONS MATRIX:
 * ========================
 *
 * FROM → TO | PENDING_PAYMENT | VERIFIED | PAID | FULFILLED | CANCELLED
 * -------------------|----------------|----------|------|-----------|----------
 * PENDING_PAYMENT | ✗ (no-op) | ✓ verify | ✗ | ✗ | ✓ reject
 * VERIFIED | ✗ | ✗ (no-op)| ✓ pay| ✗ | ✓ reject
 * PAID | ✗ | ✗ | ✗ | ✓ fulfill | ✓ cancel
 * FULFILLED | ✗ | ✗ | ✗ | ✗ (no-op) | ✗
 * CANCELLED | ✗ | ✗ | ✗ | ✗ | ✗ (no-op)
 *
 * LEGEND:
 * ✓ = ALLOWED (with preconditions)
 * ✗ = INVALID (throws exception)
 *
 * TRANSITION PRECONDITIONS:
 * =========================
 *
 * PENDING_PAYMENT → VERIFIED:
 * - Must provide valid verification code
 * - Code must match order's verificationCode
 * - Code not already used (idempotent)
 * - Order must belong to provider
 *
 * PENDING_PAYMENT → CANCELLED:
 * - No preconditions (provider can always reject)
 * - Releases inventory reservation
 * - Marks payment as failed
 *
 * VERIFIED → PAID:
 * - Must be VERIFIED (verifiedAt != null)
 * - Provider must own order
 * - Deducts inventory (permanent)
 * - Marks payment as completed
 *
 * VERIFIED → CANCELLED:
 * - Releases inventory reservation
 * - Marks payment as failed
 *
 * PAID → FULFILLED:
 * - Must be PAID
 * - Provider must own order
 * - Updates fulfillment timestamp
 *
 * PAID → CANCELLED:
 * - Must be PAID
 * - Provider must own order
 * - May require refund processing
 * - Inventory already deducted (cannot restore)
 *
 * INVALID TRANSITIONS (ALWAYS BLOCKED):
 * ====================================
 * - Any transition to PENDING_PAYMENT (orders don't go backward)
 * - FULFILLED → anything (terminal state)
 * - CANCELLED → anything (terminal state)
 * - VERIFIED → PENDING_PAYMENT (verification is permanent)
 * - PAID → VERIFIED (payment confirmation is permanent)
 * - PAID → PENDING_PAYMENT (payment is permanent)
 * - Any self-transition except FULFILLED/CANCELLED (no-op transitions)
 */