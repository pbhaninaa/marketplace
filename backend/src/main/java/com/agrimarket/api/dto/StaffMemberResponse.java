package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserRole;
import java.math.BigDecimal;
import java.util.Set;

public record StaffMemberResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String displayName,
        UserRole role,
        boolean enabled,
        boolean owner,
        StaffRateUnit rateUnit,
        BigDecimal rateAmount,
        String targetPeriod,
        BigDecimal targetValue,
        BigDecimal bonusPercentage,
        Set<ProviderPermissionKey> permissions) {}
