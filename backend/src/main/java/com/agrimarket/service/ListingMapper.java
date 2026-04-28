package com.agrimarket.service;

import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;

public final class ListingMapper {

    private ListingMapper() {}

    public static ListingResponse toResponse(Listing l) {
        Integer stockForBuyers = null;
        Integer stockQuantity = l.getStockQuantity();
        if (l.getListingType() == ListingType.SALE) {
            // Null means "not tracked" (unlimited/unknown) - keep null for the client.
            if (stockQuantity != null) {
                int reserved = l.getReservedStock() == null ? 0 : l.getReservedStock();
                stockForBuyers = Math.max(0, stockQuantity.intValue() - reserved);
            } else {
                stockForBuyers = null;
            }
        } else {
            stockForBuyers = stockQuantity;
        }
        return new ListingResponse(
                l.getId(),
                l.getProvider().getId(),
                l.getProvider().getName(),
                l.getProvider().getLocation(),
                l.getCategory().getId(),
                l.getCategory().getName(),
                l.getListingType(),
                l.getTitle(),
                l.getDescription(),
                l.getImageUrls(),
                l.getUnitPrice(),
                stockForBuyers,
                l.getRentPriceHourly(),
                l.getRentPriceDaily(),
                l.getRentPriceWeekly(),
                l.isActive());
    }
}
