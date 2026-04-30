package com.agrimarket.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PlatformSettingsUpdateRequest(
        @NotBlank String systemName,
        String bankName,
        String accountName,
        String accountNumber,
        String branchCode,
        String referenceHint,
        BigDecimal basicMonthly,
        BigDecimal premiumMonthly,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal usageFeePercent,
        @Size(max = 200) String invoiceLegalName,
        @Size(max = 2000) String invoiceAddress,
        @Size(max = 80) String invoiceVatNumber,
        @Size(max = 2000) String invoiceFooterNote) {}

