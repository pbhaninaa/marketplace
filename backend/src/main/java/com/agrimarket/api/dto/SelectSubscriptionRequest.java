package com.agrimarket.api.dto;

import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

public record SelectSubscriptionRequest(@NotNull SubscriptionPlan plan, @NotNull BillingCycle billingCycle) {}

