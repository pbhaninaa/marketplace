package com.agrimarket.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PlatformSettingsResponse(
        String systemName,
        String bankName,
        String accountName,
        String accountNumber,
        String branchCode,
        String referenceHint,
        BigDecimal basicMonthly,
        BigDecimal premiumMonthly,
        BigDecimal usageFeePercent,
        String invoiceLegalName,
        String invoiceAddress,
        String invoiceVatNumber,
        String invoiceFooterNote,
        Instant updatedAt) {}

