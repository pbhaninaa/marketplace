package com.agrimarket.service;

import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import com.agrimarket.repo.CartLineRepository;
import com.agrimarket.repo.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase inventory management based on payment status.
 *
 * - PENDING payment: items are reserved (stockReserved)
 * - PAID: items are deducted from stockQuantity
 * - CANCELLED: reserved items are released back
 */
@Service
@RequiredArgsConstructor
public class PurchaseInventoryService {

    private final ListingRepository listingRepository;
    private final CartLineRepository cartLineRepository;

    /**
     * Finalizes a paid purchase by converting reservations to actual deductions.
     * Called when order moves from PENDING_PAYMENT to PAID status.
     */
    @Transactional
    public void finalizePaidPurchase(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            if (listing == null || listing.getStockQuantity() == null)
                continue;

            // Deduct from reserved and from actual stock
            ListingStock.removeReservation(listing, item.getQuantity());
            ListingStock.deductStock(listing, item.getQuantity());
            listingRepository.save(listing);
        }
        order.setInventoryFinalized(true);
    }

    /**
     * Releases pending reservations when an order is cancelled.
     * Called when order moves to CANCELLED status with PENDING payment.
     */
    @Transactional
    public void releasePendingReservation(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            if (listing == null || listing.getStockQuantity() == null)
                continue;

            // Release the reservation
            ListingStock.removeReservation(listing, item.getQuantity());
            listingRepository.save(listing);
        }
    }

    /**
     * Restores deducted inventory when a PAID order is cancelled or rejected.
     * Called when order moves from PAID to CANCELLED status.
     */
    @Transactional
    public void restoreDeductedInventory(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            if (listing == null || listing.getStockQuantity() == null)
                continue;

            // Restore the deducted stock back to available inventory
            ListingStock.restoreStock(listing, item.getQuantity());
            listingRepository.save(listing);
        }
    }

    /**
     * Check if inventory is sufficient for an order.
     */
    public boolean hasSufficientInventory(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            if (listing == null || listing.getStockQuantity() == null) {
                return false;
            }

            int available = ListingStock.availableForSale(listing);
            if (available < item.getQuantity()) {
                return false;
            }
        }
        return true;
    }
}
