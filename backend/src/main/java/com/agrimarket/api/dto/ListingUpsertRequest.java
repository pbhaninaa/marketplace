package com.agrimarket.api.dto;

import com.agrimarket.domain.ListingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ListingUpsertRequest(
        @NotNull ListingType listingType,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        String imageUrls,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal unitPrice,
        @Min(0) Integer stockQuantity,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal rentPriceHourly,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal rentPriceDaily,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal rentPriceWeekly,
        @NotBlank @Size(max = 200) String categoryName,
        boolean active) {}
