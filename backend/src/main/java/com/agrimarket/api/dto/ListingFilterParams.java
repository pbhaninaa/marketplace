package com.agrimarket.api.dto;

import com.agrimarket.domain.ListingType;
import java.math.BigDecimal;

public record ListingFilterParams(
        Long categoryId,
        Long providerId,
        ListingType listingType,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String locationContains,
        String search) {}
