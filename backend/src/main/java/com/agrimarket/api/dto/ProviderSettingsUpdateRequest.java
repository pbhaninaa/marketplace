package com.agrimarket.api.dto;

import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.util.validation.BankAccountNumber;
import com.agrimarket.util.validation.BranchCode;
import com.agrimarket.util.validation.BusinessName;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

public record ProviderSettingsUpdateRequest(
        @NotBlank @Size(max = 255) String location,
        @Size(max = 200) @BusinessName String bankName,
        @Size(max = 200) @BusinessName String bankAccountName,
        @Size(max = 50) @BankAccountNumber String bankAccountNumber,
        @Size(max = 20) @BranchCode String bankBranchCode,
        @Size(max = 140) String bankReference,
        Set<PaymentMethod> acceptedPaymentMethods,
        boolean deliveryAvailable,
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("10000") BigDecimal deliveryPricePerKm) {}
