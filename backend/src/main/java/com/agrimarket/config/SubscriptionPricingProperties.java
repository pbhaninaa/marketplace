package com.agrimarket.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.subscription.pricing")
public record SubscriptionPricingProperties(
        BigDecimal basicMonthly,
        BigDecimal premiumMonthly) {}

