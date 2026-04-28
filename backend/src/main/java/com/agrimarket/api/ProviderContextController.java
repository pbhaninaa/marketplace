package com.agrimarket.api;

import com.agrimarket.api.dto.ProviderContextResponse;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me/context")
@RequiredArgsConstructor
public class ProviderContextController {

    private final ProviderPermissionService providerPermissionService;

    @GetMapping
    public ProviderContextResponse get(@AuthenticationPrincipal MarketUserPrincipal actor) {
        var subtype = providerPermissionService.providerSubtype(actor.getProviderId());
        var effective = providerPermissionService.effectivePermissions(actor);
        var applicable = providerPermissionService.applicableKeys(subtype);
        return new ProviderContextResponse(actor.getProviderId(), subtype, effective, applicable);
    }
}

