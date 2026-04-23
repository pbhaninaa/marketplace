package com.agrimarket.api;

import com.agrimarket.api.dto.ProviderSettingsResponse;
import com.agrimarket.api.dto.ProviderSettingsUpdateRequest;
import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderPermissionService;
import com.agrimarket.service.ProviderSettingsService;
import com.agrimarket.service.TenantAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me")
@RequiredArgsConstructor
public class ProviderSettingsController {

    private final ProviderSettingsService providerSettingsService;
    private final ProviderPermissionService providerPermissionService;

    @GetMapping("/settings")
    public ProviderSettingsResponse get(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        // Any provider user can read settings (so they know payment methods/bank details); sensitive edit is restricted.
        return providerSettingsService.get(user);
    }

    @PatchMapping("/settings")
    public ProviderSettingsResponse update(
            @AuthenticationPrincipal MarketUserPrincipal user, @Valid @RequestBody ProviderSettingsUpdateRequest req) {
        TenantAccess.requireProviderUser(user);
        // Only owner/admin (or permission key if later added) can update.
        if (user.getRole() != com.agrimarket.domain.UserRole.PROVIDER_OWNER
                && user.getRole() != com.agrimarket.domain.UserRole.PROVIDER_ADMIN) {
            // Optionally allow by permission key if you decide to delegate settings later.
            providerPermissionService.require(user, ProviderPermissionKey.TEAM_MANAGE);
        }
        return providerSettingsService.update(user, req);
    }
}

