package com.agrimarket.api;

import com.agrimarket.api.dto.ProviderContextResponse;
import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.ProviderSubtype;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderPermissionService;
import com.agrimarket.service.TenantAccess;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me")
@RequiredArgsConstructor
public class ProviderContextController {

    private final ProviderPermissionService providerPermissionService;

    @GetMapping("/context")
    public ProviderContextResponse context(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        ProviderSubtype subtype = providerPermissionService.providerSubtype(user.getProviderId());
        Set<ProviderPermissionKey> applicable = providerPermissionService.applicableKeys(subtype);
        Set<ProviderPermissionKey> effective = providerPermissionService.effectivePermissions(user);
        return new ProviderContextResponse(user.getProviderId(), subtype, effective, applicable);
    }
}

