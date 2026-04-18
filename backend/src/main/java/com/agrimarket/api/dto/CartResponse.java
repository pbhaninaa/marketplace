package com.agrimarket.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import com.agrimarket.domain.PaymentMethod;

public record CartResponse(
        String sessionKey,
        Long lockedProviderId,
        String lockedProviderName,
        String lockedProviderLocation,
        String lockedProviderBankName,
        String lockedProviderBankAccountName,
        String lockedProviderBankAccountNumber,
        String lockedProviderBankBranchCode,
        String lockedProviderBankReference,
        Set<PaymentMethod> lockedProviderAcceptedPaymentMethods,
        Boolean lockedProviderDeliveryAvailable,
        BigDecimal lockedProviderDeliveryPricePerKm,
        List<CartLineResponse> lines,
        BigDecimal estimatedTotal) {

    public record CartLineResponse(
            Long lineId,
            Long listingId,
            String title,
            String listingType,
            int quantity,
            BigDecimal lineTotal,
            String rentalStart,
            String rentalEnd,
            Integer availableStock) {}
}
