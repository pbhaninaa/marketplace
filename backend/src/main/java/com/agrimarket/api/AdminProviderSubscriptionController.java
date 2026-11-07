package com.agrimarket.api;

import com.agrimarket.api.dto.AdminProviderSubscriptionResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.OwnerSubscriptionOverrideService;
import com.agrimarket.service.PlatformOwnerService;
import com.agrimarket.service.SubscriptionService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider subscription as seen by platform admins, plus platform-owner overrides (extend / end the
 * free trial, set plan + active-until without a Peach payment). Any platform admin may read; only the
 * platform owner (first admin, see {@link PlatformOwnerService}) may change; other admins get 403.
 */
@RestController
@RequestMapping("/api/admin/providers/{providerId}/subscription")
@RequiredArgsConstructor
public class AdminProviderSubscriptionController {

    private final ProviderRepository providerRepository;
    private final SubscriptionService subscriptionService;
    private final OwnerSubscriptionOverrideService overrideService;
    private final PlatformOwnerService platformOwnerService;

    public record ExtendTrialRequest(Integer days, String reason) {}

    public record EndTrialRequest(String reason) {}

    public record SetSubscriptionRequest(SubscriptionPlan plan, LocalDate activeUntil, String reason) {}

    @GetMapping
    public AdminProviderSubscriptionResponse get(
            @AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable Long providerId) {
        requireAdmin(actor);
        return view(actor, providerId);
    }

    @PostMapping("/extend-trial")
    public AdminProviderSubscriptionResponse extendTrial(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long providerId,
            @RequestBody(required = false) ExtendTrialRequest req) {
        platformOwnerService.requireOwner(actor);
        if (req == null || req.days() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Enter the number of days");
        }
        overrideService.extendTrial(providerId, req.days(), req.reason(), actor);
        return view(actor, providerId);
    }

    @PostMapping("/end-trial")
    public AdminProviderSubscriptionResponse endTrial(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long providerId,
            @RequestBody(required = false) EndTrialRequest req) {
        platformOwnerService.requireOwner(actor);
        overrideService.endTrial(providerId, req != null ? req.reason() : null, actor);
        return view(actor, providerId);
    }

    @PutMapping("/override")
    public AdminProviderSubscriptionResponse setSubscription(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long providerId,
            @RequestBody(required = false) SetSubscriptionRequest req) {
        platformOwnerService.requireOwner(actor);
        if (req == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Request body is required");
        }
        overrideService.setSubscription(providerId, req.plan(), req.activeUntil(), req.reason(), actor);
        return view(actor, providerId);
    }

    private AdminProviderSubscriptionResponse view(MarketUserPrincipal actor, Long providerId) {
        var provider = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        var snap = subscriptionService.resolveStatusSnapshot(providerId);
        var sub = snap.subscription();
        var history = overrideService.history(providerId).stream()
                .map(a -> new AdminProviderSubscriptionResponse.OverrideEntry(
                        a.getId(), a.getAction(), a.getActorEmail(), a.getReason(), a.getDetails(), a.getCreatedAt()))
                .toList();
        return new AdminProviderSubscriptionResponse(
                provider.getId(),
                provider.getName(),
                platformOwnerService.isPlatformOwner(actor),
                snap.valid(),
                sub != null ? sub.getPlan() : null,
                sub != null ? sub.getStatus() : null,
                sub != null ? sub.getExpiresAt() : null,
                OwnerSubscriptionOverrideService.isOverride(sub),
                snap.onTrial(),
                snap.trialStartedAt(),
                snap.trialEndsAt(),
                snap.trialDaysRemaining(),
                history);
    }

    private static void requireAdmin(MarketUserPrincipal actor) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        if (actor.getRole() != UserRole.PLATFORM_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH", "Forbidden");
        }
    }
}
