package com.agrimarket.api.dto;

import com.agrimarket.domain.ProviderSubtype;
import com.agrimarket.util.validation.BusinessName;
import com.agrimarket.util.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param emailVerificationCode code emailed by {@code POST /api/public/provider/register/email-code};
 *     required when {@code app.signup.require-email-verification=true}.
 */
public record ProviderRegisterRequest(
        @NotBlank @Size(min = 2, max = 200) @BusinessName String businessName,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 255) String location,
        ProviderSubtype subtype,
        @NotBlank @Size(max = 254) @ValidEmail String ownerEmail,
        @NotBlank @Size(min = 8, max = 100) String password,
        /** Optional referral code (typed, or pre-filled from an invite link). */
        @Size(max = 32) String referralCode,
        @Size(max = 12) String emailVerificationCode) {}
