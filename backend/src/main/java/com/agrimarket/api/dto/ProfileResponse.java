package com.agrimarket.api.dto;

import com.agrimarket.domain.UserRole;
import java.time.Instant;

public record ProfileResponse(
        Long id,
        String email,
        UserRole role,
        String firstName,
        String lastName,
        String displayName,
        String phoneNumber,
        String providerName,
        Instant createdAt) {}
