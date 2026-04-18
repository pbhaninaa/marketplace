package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserRole;
import java.math.BigDecimal;
import java.util.Set;

public record StaffMemberResponse(
        Long id,
        String email,
        UserRole role,
        boolean enabled,
        boolean owner,
        StaffRateUnit rateUnit,
        BigDecimal rateAmount,
        Set<ProviderPermissionKey> permissions) {}
