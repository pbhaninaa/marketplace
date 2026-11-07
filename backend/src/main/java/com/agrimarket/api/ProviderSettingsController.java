package com.agrimarket.api;

import com.agrimarket.api.dto.ProviderSettingsResponse;
import com.agrimarket.api.dto.ProviderSettingsUpdateRequest;
import com.agrimarket.security.AccessGuard;
import com.agrimarket.security.MarketUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import com.agrimarket.service.ProviderSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/provider/me/settings")
@RequiredArgsConstructor
public class ProviderSettingsController {

    private final ProviderSettingsService providerSettingsService;

    @GetMapping
    public ProviderSettingsResponse get(@AuthenticationPrincipal MarketUserPrincipal actor) {
        return providerSettingsService.get(actor);
    }

    @PatchMapping
    @PreAuthorize(AccessGuard.PROVIDER_MANAGER)
    public ProviderSettingsResponse update(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @Valid @RequestBody ProviderSettingsUpdateRequest req) {
        return providerSettingsService.update(actor, req);
    }
}

