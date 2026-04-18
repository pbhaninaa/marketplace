package com.agrimarket.api.dto;

import com.agrimarket.domain.PaymentMethod;
import java.util.Set;

public record ProviderSettingsResponse(
        Long providerId,
        String providerName,
        String location,
        String bankName,
        String bankAccountName,
        String bankAccountNumber,
        String bankBranchCode,
        String bankReference,
        Set<PaymentMethod> acceptedPaymentMethods) {}

