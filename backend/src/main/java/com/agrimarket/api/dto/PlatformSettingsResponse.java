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
        Instant updatedAt,
        String onlinePaymentGateway,
        BigDecimal onlineOrderFeePercent,
        /** Gateway new online payments go through right now (null: online payments unavailable). */
        String activeOnlineGateway,
        boolean payfastConfigured,
        boolean peachConfigured) {}

