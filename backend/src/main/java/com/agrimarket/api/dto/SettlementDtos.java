package com.agrimarket.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Seller settlement (payout) DTOs for the admin payouts page and the provider's read-only view. */
public final class SettlementDtos {

    private SettlementDtos() {}

    /** One online payment with its settlement figures. */
    public record PaymentSettlementRow(
            Long paymentId,
            Long providerId,
            String providerName,
            /** ORDER or RENTAL. */
            String kind,
            Long referenceId,
            String verificationCode,
            String buyerName,
            /** Current order / booking status. */
            String itemStatus,
            String gateway,
            BigDecimal amount,
            BigDecimal platformFeePercent,
            BigDecimal platformFee,
            BigDecimal sellerPayoutAmount,
            String settlementStatus,
            Instant paidAt,
            Instant paidOutAt,
            String paidOutBy,
            String payoutReference) {}

    /** Total owed to one seller across their HELD payments, with the bank details to pay into. */
    public record SellerOwedRow(
            Long providerId,
            String providerName,
            String bankName,
            String bankAccountName,
            String bankAccountNumber,
            String bankBranchCode,
            int paymentCount,
            BigDecimal grossAmount,
            BigDecimal platformFee,
            BigDecimal amountOwed) {}

    public record PayoutSummaryResponse(
            BigDecimal onlineOrderFeePercent,
            BigDecimal totalOwed,
            int refundDueCount,
            BigDecimal refundDueTotal,
            List<SellerOwedRow> sellers) {}

    public record MarkPaidOutRequest(
            @NotNull Long providerId,
            /** Empty or null: every HELD payment of the seller. */
            List<Long> paymentIds,
            @NotBlank @Size(max = 120) String reference) {}

    public record MarkRefundedRequest(
            @NotNull @Size(min = 1, max = 500) List<Long> paymentIds,
            @NotBlank @Size(max = 120) String reference) {}

    public record PayoutActionResult(
            int count, BigDecimal total, String settlementStatus, String reference, Instant at) {}

    /** Provider's own online payments: fee, amount owed and payout status. */
    public record ProviderPayoutsResponse(
            BigDecimal onlineOrderFeePercent,
            BigDecimal amountOwed,
            BigDecimal amountPaidOut,
            BigDecimal platformFees,
            List<PaymentSettlementRow> payments) {}
}
