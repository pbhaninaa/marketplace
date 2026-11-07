package com.agrimarket.api.dto;

import com.agrimarket.util.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirstAdminRequest(
        @NotBlank @Size(max = 254) @ValidEmail String email,
        @NotBlank @Size(min = 8, max = 100) String password) {}
