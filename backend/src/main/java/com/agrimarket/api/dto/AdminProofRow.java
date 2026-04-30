package com.agrimarket.api.dto;

import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionProofStatus;
import java.time.Instant;

public record AdminProofRow(
        Long proofId,
        Long providerId,
        String providerName,
        Long subscriptionId,
        SubscriptionPlan plan,
        BillingCycle billingCycle,
        SubscriptionProofStatus status,
        Instant createdAt) {}

