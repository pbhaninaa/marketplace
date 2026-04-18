package com.agrimarket.api.dto;

import com.agrimarket.domain.UserRole;

/** Minimal auth response for guest client sessions. */
public record ClientOtpVerifyResponse(String token, UserRole role, String clientTarget) {}

