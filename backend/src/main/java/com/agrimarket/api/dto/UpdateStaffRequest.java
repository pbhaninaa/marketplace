package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

public record UpdateStaffRequest(
        @NotNull UserRole role,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 40) String phoneNumber,
        @NotNull StaffRateUnit rateUnit,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal rateAmount,
        String targetPeriod,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal targetValue,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal bonusPercentage,
        boolean enabled,
        Set<ProviderPermissionKey> permissions) {}
