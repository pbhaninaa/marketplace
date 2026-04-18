package com.agrimarket.api.dto;

import com.agrimarket.domain.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ProviderSettingsUpdateRequest(
        @NotBlank @Size(max = 500) String location,
        @Size(max = 200) String bankName,
        @Size(max = 200) String bankAccountName,
        @Size(max = 50) String bankAccountNumber,
        @Size(max = 20) String bankBranchCode,
        @Size(max = 140) String bankReference,
        Set<PaymentMethod> acceptedPaymentMethods) {}

