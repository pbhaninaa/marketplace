package com.agrimarket.api.dto;

import com.agrimarket.domain.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * A cart can hold items from several sellers. {@link #sellers} groups the lines per seller with that
 * seller's own payment methods, delivery and bank details; each group is checked out separately.
 *
 * <p>The {@code lockedProvider*} fields are kept for older clients: they describe the seller when the
 * cart holds exactly one, and are null when it holds several.
 */
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
        BigDecimal estimatedTotal,
        List<SellerGroup> sellers) {

    public record CartLineResponse(
            Long lineId,
            Long listingId,
            String title,
            String listingType,
            int quantity,
            BigDecimal lineTotal,
            String rentalStart,
            String rentalEnd,
            Integer availableStock,
            Long providerId,
            String providerName) {}

    /** One seller's part of the cart: checked out on its own, with its own payment and delivery. */
    public record SellerGroup(
            Long providerId,
            String providerName,
            String providerLocation,
            String bankName,
            String bankAccountName,
            String bankAccountNumber,
            String bankBranchCode,
            String bankReference,
            Set<PaymentMethod> acceptedPaymentMethods,
            boolean deliveryAvailable,
            BigDecimal deliveryPricePerKm,
            List<Long> lineIds,
            int itemCount,
            BigDecimal subtotal) {}
}
