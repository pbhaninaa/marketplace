package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserRole;
import com.agrimarket.util.validation.PersonName;
import com.agrimarket.util.validation.ValidEmail;
import com.agrimarket.util.validation.ValidPhone;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

public record CreateStaffRequest(
        @NotBlank @Size(max = 254) @ValidEmail String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Size(max = 100) @PersonName String firstName,
        @Size(max = 100) @PersonName String lastName,
        @Size(max = 40) @ValidPhone String phoneNumber,
        @NotNull UserRole role,
        @NotNull StaffRateUnit rateUnit,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @DecimalMax("10000000") BigDecimal rateAmount,
        String targetPeriod,
        @DecimalMin(value = "0.0", inclusive = true) @DecimalMax("10000000") BigDecimal targetValue,
        @DecimalMin(value = "0.0", inclusive = true) @DecimalMax("100") BigDecimal bonusPercentage,
        Set<ProviderPermissionKey> permissions) {}
