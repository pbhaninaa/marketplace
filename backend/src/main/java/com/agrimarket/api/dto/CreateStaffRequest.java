package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

public record CreateStaffRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull UserRole role,
        @NotNull StaffRateUnit rateUnit,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal rateAmount,
        Set<ProviderPermissionKey> permissions) {}
