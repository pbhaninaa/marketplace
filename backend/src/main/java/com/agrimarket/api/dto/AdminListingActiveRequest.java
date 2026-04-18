package com.agrimarket.api.dto;

import jakarta.validation.constraints.NotNull;

public record AdminListingActiveRequest(@NotNull Boolean active) {}

