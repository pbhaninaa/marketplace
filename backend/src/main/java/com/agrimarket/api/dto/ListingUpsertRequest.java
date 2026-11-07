package com.agrimarket.api.dto;

import com.agrimarket.domain.ListingType;
import com.agrimarket.util.validation.MediaUrls;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ListingUpsertRequest(
        @NotNull ListingType listingType,
        @NotBlank @Size(min = 2, max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 4000) @MediaUrls String imageUrls,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("10000000") BigDecimal unitPrice,
        @Min(0) @Max(1000000) Integer stockQuantity,
        @DecimalMin(value = "0.0", inclusive = true) @DecimalMax("10000000") BigDecimal rentPriceHourly,
        @DecimalMin(value = "0.0", inclusive = true) @DecimalMax("10000000") BigDecimal rentPriceDaily,
        @DecimalMin(value = "0.0", inclusive = true) @DecimalMax("10000000") BigDecimal rentPriceWeekly,
        @NotBlank @Size(max = 200) String categoryName,
        boolean active) {}
