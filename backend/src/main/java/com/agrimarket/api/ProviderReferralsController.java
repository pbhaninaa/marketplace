package com.agrimarket.api;

import com.agrimarket.api.dto.ReferralDtos.ClaimRequest;
import com.agrimarket.api.dto.ReferralDtos.MyReferralsResponse;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ReferralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "My referrals" for provider users. Deliberately outside {@code /api/provider/me} so a provider
 * whose subscription lapsed can still see their code and earned commission.
 */
@RestController
@RequestMapping("/api/provider/referrals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROVIDER_OWNER','PROVIDER_ADMIN','PROVIDER_STAFF','PROVIDER_VIEWER')")
public class ProviderReferralsController {

    private final ReferralService referralService;

    /** Own code, totals, referred businesses, and who referred this business (or whether it still can say). */
    @GetMapping
    public MyReferralsResponse mine(@AuthenticationPrincipal MarketUserPrincipal actor) {
        return referralService.myReferrals(actor.getUserId());
    }

    /** Add a referral code after sign-up (owner/admin, within the claim window, before the first payment). */
    @PostMapping("/claim")
    public MyReferralsResponse claim(
            @AuthenticationPrincipal MarketUserPrincipal actor, @Valid @RequestBody ClaimRequest req) {
        referralService.claim(actor.getUserId(), req.code());
        return referralService.myReferrals(actor.getUserId());
    }
}
