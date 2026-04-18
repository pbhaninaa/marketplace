package com.agrimarket.api.dto;

import com.agrimarket.domain.ListingType;
import java.math.BigDecimal;

public record ListingResponse(
        Long id,
        Long providerId,
        String providerName,
        String providerLocation,
        Long categoryId,
        String categoryName,
        ListingType listingType,
        String title,
        String description,
        String imageUrls,
        BigDecimal unitPrice,
        Integer stockQuantity,
        BigDecimal rentPriceHourly,
        BigDecimal rentPriceDaily,
        BigDecimal rentPriceWeekly,
        boolean active) {}
