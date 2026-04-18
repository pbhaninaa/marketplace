package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.ProviderSubtype;
import java.util.Set;

public record ProviderContextResponse(
        Long providerId,
        ProviderSubtype providerSubtype,
        Set<ProviderPermissionKey> effectivePermissions,
        Set<ProviderPermissionKey> applicablePermissions) {}

