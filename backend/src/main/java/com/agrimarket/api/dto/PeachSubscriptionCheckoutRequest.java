package com.agrimarket.api.dto;

import com.agrimarket.domain.PeachPaymentMethod;
import jakarta.validation.constraints.NotNull;

/** Body for initiating a Peach Hosted Checkout for a provider subscription quote. */
public record PeachSubscriptionCheckoutRequest(
        @NotNull Long intentId,
        @NotNull PeachPaymentMethod peachPaymentMethod
) {}
