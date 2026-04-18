package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Set;

public record UpdateStaffRequest(
        @NotNull UserRole role,
        @NotNull StaffRateUnit rateUnit,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal rateAmount,
        boolean enabled,
        Set<ProviderPermissionKey> permissions) {}
