package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderSubtype;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProviderRegisterRequest(
        @NotBlank @Size(max = 200) String businessName,
        @NotBlank @Size(max = 4000) String description,
        @NotBlank @Size(max = 500) String location,
        ProviderSubtype subtype,
        @NotBlank @Email String ownerEmail,
        @NotBlank @Size(min = 8, max = 100) String password) {}
