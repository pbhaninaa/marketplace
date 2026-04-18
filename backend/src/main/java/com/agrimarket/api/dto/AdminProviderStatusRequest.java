package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderStatus;
import jakarta.validation.constraints.NotNull;

public record AdminProviderStatusRequest(@NotNull ProviderStatus status) {}
