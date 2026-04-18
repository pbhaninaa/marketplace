package com.agrimarket.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientOtpVerifyRequest(
        @NotBlank @Size(max = 200) String target, @NotBlank @Size(min = 4, max = 12) String code) {}

