package com.agrimarket.api.dto;

import com.agrimarket.domain.UserRole;

public record LoginResponse(String token, UserRole role, Long providerId, String email, String displayName) {}
