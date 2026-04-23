package com.agrimarket.order;

import java.util.UUID;

/**
 * INVENTORY SERVICE INTERFACE
 *
 * Handles inventory reservations and deductions for the order state machine.
 * Implementations should ensure atomic operations to prevent race conditions.
 */
public interface InventoryService {

    /**
     * Reserves inventory for an order (temporary hold).
     * Called when order is created (PENDING_PAYMENT status).
     *
     * @param listingId The listing to reserve inventory for
     * @param quantity  Quantity to reserve
     * @throws InsufficientInventoryException if not enough inventory available
     */
    void reserveInventory(UUID listingId, int quantity) throws InsufficientInventoryException;

    /**
     * Releases a previous inventory reservation.
     * Called when order is cancelled from PENDING_PAYMENT/VERIFIED status.
     *
     * @param listingId The listing to release reservation for
     * @param quantity  Quantity to release
     */
    void releaseReservation(UUID listingId, int quantity);

    /**
     * Permanently deducts inventory (converts reservation to deduction).
     * Called when order transitions to PAID status.
     *
     * @param listingId The listing to deduct inventory from
     * @param quantity  Quantity to deduct
     * @throws InsufficientInventoryException if reservation was invalid
     */
    void deductInventory(UUID listingId, int quantity) throws InsufficientInventoryException;

    /**
     * Checks if sufficient inventory is available for a listing.
     *
     * @param listingId The listing to check
     * @param quantity  Required quantity
     * @return true if inventory is available
     */
    boolean isInventoryAvailable(UUID listingId, int quantity);

    /**
     * Exception thrown when inventory operations fail due to insufficient stock.
     */
    class InsufficientInventoryException extends RuntimeException {
        public InsufficientInventoryException(String message) {
            super(message);
        }
    }
}