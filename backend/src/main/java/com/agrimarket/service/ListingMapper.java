package com.agrimarket.service;

import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;

public final class ListingMapper {

    private ListingMapper() {}

    public static ListingResponse toResponse(Listing l) {
        Integer stockForBuyers =
                l.getListingType() == ListingType.SALE && l.getStockQuantity() != null
                        ? ListingStock.availableForSale(l)
                        : l.getStockQuantity();
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
