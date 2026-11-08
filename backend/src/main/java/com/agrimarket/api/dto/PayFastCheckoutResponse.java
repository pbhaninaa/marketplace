package com.agrimarket.api.dto;

import java.util.Map;

/**
 * Signed PayFast hosted checkout: the browser POSTs {@code fields} (in this order) to
 * {@code processUrl}. {@code paymentRef} is the m_payment_id used on the return page.
 */
public record PayFastCheckoutResponse(String processUrl, Map<String, String> fields, String paymentRef) {}
