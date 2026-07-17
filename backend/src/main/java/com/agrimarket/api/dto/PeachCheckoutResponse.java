package com.agrimarket.api.dto;

/** Response returned to the client after a Peach Hosted Checkout session is created. */
public record PeachCheckoutResponse(String redirectUrl, String checkoutId, String merchantTransactionId) {}
