package com.agrimarket.api;

import com.agrimarket.api.dto.ProviderSubscriptionStatusResponse;
import com.agrimarket.api.dto.SelectSubscriptionRequest;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.SubscriptionService;
import com.agrimarket.service.TenantAccess;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me/subscription")
@RequiredArgsConstructor
public class ProviderSubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/status")
    public ProviderSubscriptionStatusResponse status(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        var active = subscriptionService.currentActive(user.getProviderId()).orElse(null);
        if (active == null) {
            return new ProviderSubscriptionStatusResponse(false, null, null, null, null);
        }
        boolean valid = active.getExpiresAt() != null && active.getExpiresAt().isAfter(Instant.now());
        return new ProviderSubscriptionStatusResponse(
                valid, active.getPlan(), active.getBillingCycle(), active.getStatus(), active.getExpiresAt());
    }

    @PostMapping("/select")
    public ProviderSubscriptionStatusResponse select(
            @AuthenticationPrincipal MarketUserPrincipal user, @Valid @RequestBody SelectSubscriptionRequest req) {
        TenantAccess.requireProviderUser(user);
        var s = subscriptionService.selectPlan(user.getProviderId(), req.plan(), req.billingCycle());
        boolean valid = s.getExpiresAt() != null && s.getExpiresAt().isAfter(Instant.now());
        return new ProviderSubscriptionStatusResponse(
                valid, s.getPlan(), s.getBillingCycle(), s.getStatus(), s.getExpiresAt());
    }
}

