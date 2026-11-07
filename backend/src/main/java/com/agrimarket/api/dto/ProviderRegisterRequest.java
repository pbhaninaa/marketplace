package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderSubtype;
import com.agrimarket.util.validation.BusinessName;
import com.agrimarket.util.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProviderRegisterRequest(
        @NotBlank @Size(min = 2, max = 200) @BusinessName String businessName,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 255) String location,
        ProviderSubtype subtype,
        @NotBlank @Size(max = 254) @ValidEmail String ownerEmail,
        @NotBlank @Size(min = 8, max = 100) String password) {}
