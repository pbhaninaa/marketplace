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
 * Simplified inventory management based on payment status.
 * 
 * - PENDING: items are held/reserved
 * - CONFIRMED: items are deducted from available inventory
 * - REJECTED/CANCELLED: items are released back to inventory
 */
@Service
@RequiredArgsConstructor
public class PurchaseInventoryService {

    private final ListingRepository listingRepository;
    private final CartLineRepository cartLineRepository;

    /**
     * Deduct inventory when payment is confirmed for an order.
     */
    @Transactional
    public void deductInventoryForConfirmedOrder(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            if (listing == null) continue;

            listing.setAvailableQuantity(listing.getAvailableQuantity() - item.getQuantity());
            listingRepository.save(listing);
        }
    }

    /**
     * Release inventory back when payment is rejected or order is cancelled.
     */
    @Transactional
    public void releaseInventoryForCancelledOrder(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            if (listing == null) continue;

            listing.setAvailableQuantity(listing.getAvailableQuantity() + item.getQuantity());
            listingRepository.save(listing);
        }
    }

    /**
     * Check if inventory is sufficient for an order.
     */
    public boolean hasSufficientInventory(Order order) {
        for (CartLine item : order.getLines()) {
            Listing listing = item.getListing();
            if (listing == null || listing.getAvailableQuantity() < item.getQuantity()) {
                return false;
            }
        }
        return true;
    }
}

