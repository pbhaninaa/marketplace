package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderStatus;

public record ProviderResponse(
        Long id, String name, String slug, String description, String location, ProviderStatus status) {}
