package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import org.springframework.http.HttpStatus;

/** Helpers for sale listings: {@code stockQuantity} is on-hand; {@code reservedStock} is held for unpaid orders. */
public final class ListingStock {

    private ListingStock() {}

    /** Units buyers can still purchase (0 if sold out / fully reserved). Null stockQuantity means not tracked here. */
    public static int availableForSale(Listing l) {
        if (l.getListingType() != ListingType.SALE || l.getStockQuantity() == null) {
            return Integer.MAX_VALUE;
        }
        int reserved = l.getReservedStock() == null ? 0 : l.getReservedStock();
        return Math.max(0, l.getStockQuantity() - reserved);
    }

    public static void addReservation(Listing listing, int quantity) {
        if (listing.getListingType() != ListingType.SALE || listing.getStockQuantity() == null) {
            return;
        }
        if (quantity < 1) {
            return;
        }
        int available = availableForSale(listing);
        if (available < quantity) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INSUFFICIENT_STOCK",
                    "Not enough available stock for '" + listing.getTitle() + "'. Available: "
                            + available + ", Requested: " + quantity);
        }
        int reserved = listing.getReservedStock() == null ? 0 : listing.getReservedStock();
        listing.setReservedStock(reserved + quantity);
    }
}
