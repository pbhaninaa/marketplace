package com.agrimarket.service;

import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages inventory operations based on order payment status.
 * 
 * - PENDING: Inventory is RESERVED (not available for other orders)
 * - CONFIRMED: Inventory is DEDUCTED (items removed from stock)
 * - REJECTED/CANCELLED: Inventory is RELEASED (items return to available stock)
 */
@Service
@RequiredArgsConstructor
public class InventoryManagementService {

    /**
     * Deducts inventory items when payment is confirmed.
     * Items are no longer available for sale.
     */
    @Transactional
    public void deductInventoryForOrder(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            int quantity = item.getQuantity();

            // Deduct from available inventory
            listing.setAvailableQuantity(listing.getAvailableQuantity() - quantity);
            // This would normally call a repository to save listing
        }
    }

    /**
     * Releases reserved inventory when payment is rejected or order is cancelled.
     * Items return to available inventory.
     */
    @Transactional
    public void releaseInventoryForOrder(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            int quantity = item.getQuantity();

            // Release back to available inventory
            listing.setAvailableQuantity(listing.getAvailableQuantity() + quantity);
            // This would normally call a repository to save listing
        }
    }

    /**
     * Check if sufficient inventory is available for an order.
     */
    public boolean hasAvailableInventory(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            if (listing.getAvailableQuantity() < item.getQuantity()) {
                return false;
            }
        }
        return true;
    }
}
