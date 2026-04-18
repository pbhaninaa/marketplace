package com.agrimarket.api.dto;

import com.agrimarket.domain.UserRole;

public record SupportUserSearchResponse(
        Long id, String email, String displayName, UserRole role, boolean enabled, Long providerId) {}

