package com.agrimarket.api.dto;

import com.agrimarket.domain.ListingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ListingUpsertRequest(
        @NotNull ListingType listingType,
        @NotBlank String title,
        String description,
        String imageUrls,
        @NotNull BigDecimal unitPrice,
        Integer stockQuantity,
        BigDecimal rentPriceHourly,
        BigDecimal rentPriceDaily,
        BigDecimal rentPriceWeekly,
        @NotBlank @Size(max = 200) String categoryName,
        boolean active) {}
