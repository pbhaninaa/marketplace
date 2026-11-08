package com.agrimarket.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Online checkout started through the active gateway. Peach: send the browser to {@code redirectUrl}.
 * PayFast: POST {@code payfast.fields} to {@code payfast.processUrl}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OnlineCheckoutResponse(
        String gateway, String paymentRef, String redirectUrl, PayFastCheckoutResponse payfast) {}
