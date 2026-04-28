package com.agrimarket.api.dto;

import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import java.time.Instant;

public record ProviderSubscriptionStatusResponse(
        boolean valid,
        SubscriptionPlan plan,
        BillingCycle billingCycle,
        SubscriptionStatus status,
        Instant expiresAt) {}

