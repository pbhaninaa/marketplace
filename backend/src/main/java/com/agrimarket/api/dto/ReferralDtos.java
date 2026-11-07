package com.agrimarket.api.dto;

import com.agrimarket.domain.ReferralSource;
import com.agrimarket.domain.ReferralStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request / response shapes for the referral programme. */
public final class ReferralDtos {

    private ReferralDtos() {}

    // ---------- settings (platform admin) ----------

    public record SettingsResponse(
            BigDecimal commissionPercent, int claimWindowDays, String updatedBy, Instant updatedAt) {}

    public record SettingsUpdateRequest(
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionPercent,
            @NotNull @Min(1) @Max(365) Integer claimWindowDays) {}

    // ---------- public ----------

    /** Sign-up page check: is this code valid, and whose is it (business name only). */
    public record CodeLookupResponse(boolean valid, String referrerName) {}

    // ---------- provider user ("My referrals") ----------

    public record ClaimRequest(@NotBlank @Size(max = 32) String code) {}

    /** Who referred the signed-in user's business, or whether they can still add a code (and until when). */
    public record InviteStatus(String referredByName, boolean canClaim, Instant claimDeadline, String reason) {}

    /** One business the signed-in user referred. No emails or admin notes are exposed. */
    public record MyReferralRow(
            Long id,
            String businessName,
            Instant joinedAt,
            ReferralStatus status,
            BigDecimal commissionPercent,
            BigDecimal commissionAmount,
            Instant earnedAt,
            Instant paidOutAt) {}

    public record MyReferralsResponse(
            String code,
            BigDecimal currentCommissionPercent,
            int claimWindowDays,
            long invitedCount,
            long qualifiedCount,
            BigDecimal earnedAmount,
            BigDecimal awaitingPayoutAmount,
            BigDecimal paidOutAmount,
            InviteStatus invite,
            List<MyReferralRow> referrals) {}

    // ---------- admin ----------

    public record AdminReferralRow(
            Long id,
            Long referrerUserId,
            String referrerEmail,
            String referrerName,
            String referrerBusiness,
            String referralCode,
            Long refereeProviderId,
            String refereeBusiness,
            String refereeEmail,
            ReferralSource source,
            ReferralStatus status,
            BigDecimal paymentAmount,
            BigDecimal commissionPercent,
            BigDecimal commissionAmount,
            String qualifyingPaymentRef,
            Instant createdAt,
            Instant earnedAt,
            Instant paidOutAt,
            String paidOutBy,
            String payoutNote) {}

    public record AdminStats(
            long totalReferrals,
            long pendingCount,
            long earnedCount,
            long paidCount,
            BigDecimal awaitingPayoutAmount,
            BigDecimal paidOutAmount) {}

    public record MarkPaidRequest(@Size(max = 1000) String note) {}
}
