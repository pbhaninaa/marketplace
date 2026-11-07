package com.agrimarket.api.dto;

import com.agrimarket.domain.SubscriptionOverrideAction;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.List;

/** Admin view of a provider's subscription/trial plus platform-owner override history. */
public record AdminProviderSubscriptionResponse(
        Long providerId,
        String providerName,
        /** True when the caller is the platform owner and may use the override endpoints. */
        boolean platformOwner,
        boolean valid,
        SubscriptionPlan plan,
        SubscriptionStatus status,
        Instant expiresAt,
        /** True when the current subscription row was set by an owner override (no payment). */
        boolean overridden,
        boolean onTrial,
        Instant trialStartedAt,
        Instant trialEndsAt,
        long trialDaysRemaining,
        List<OverrideEntry> history) {

    public record OverrideEntry(
            Long id,
            SubscriptionOverrideAction action,
            String actorEmail,
            String reason,
            String details,
            Instant createdAt) {}
}
