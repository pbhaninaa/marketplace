package com.agrimarket.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CartAddRequest(
        @NotNull Long listingId,
        @NotNull @Min(1) Integer quantity,
        Instant rentalStart,
        Instant rentalEnd) {}
