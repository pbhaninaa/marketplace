package com.agrimarket.api;

import com.agrimarket.api.dto.ProviderSubscriptionStatusResponse;
import com.agrimarket.api.dto.BankDetailsResponse;
import com.agrimarket.api.dto.SelectSubscriptionRequest;
import com.agrimarket.api.dto.SubscriptionQuoteResponse;
import com.agrimarket.api.dto.UploadProofResponse;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.SubscriptionPaymentProofService;
import com.agrimarket.service.SubscriptionQuoteService;
import com.agrimarket.service.SubscriptionService;
import com.agrimarket.service.TenantAccess;
import com.agrimarket.service.PlatformSettingsService;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.agrimarket.domain.SubscriptionPlan;

@RestController
@RequestMapping("/api/provider/me/subscription")
@RequiredArgsConstructor
public class ProviderSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionPaymentProofService proofService;
    private final SubscriptionQuoteService quoteService;
    private final PlatformSettingsService platformSettingsService;

    @GetMapping("/status")
    public ProviderSubscriptionStatusResponse status(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        var cur = subscriptionService.currentLatest(user.getProviderId()).orElse(null);
        if (cur == null) {
            return new ProviderSubscriptionStatusResponse(false, null, null, null, null, null, null);
        }
        boolean valid = cur.getStatus() == com.agrimarket.domain.SubscriptionStatus.ACTIVE
                && cur.getExpiresAt() != null
                && cur.getExpiresAt().isAfter(Instant.now());
        return new ProviderSubscriptionStatusResponse(
                valid,
                cur.getPlan(),
                cur.getBillingCycle(),
                cur.getStatus(),
                cur.getExpiresAt(),
                cur.getAmountDue(),
                cur.getPaymentReference());
    }

    @GetMapping("/bank-details")
    public BankDetailsResponse bankDetails(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        var s = platformSettingsService.getOrCreate();
        return new BankDetailsResponse(
                s.getBankName(),
                s.getAccountName(),
                s.getAccountNumber(),
                s.getBranchCode(),
                s.getReferenceHint());
    }

    @PostMapping("/proof")
    public UploadProofResponse uploadProof(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @RequestParam("intentId") Long intentId,
            @RequestParam("file") MultipartFile file) {
        TenantAccess.requireProviderUser(user);
        var proof = proofService.upload(user.getProviderId(), intentId, file);
        return new UploadProofResponse(proof.getId(), proof.getStatus(), proof.getCreatedAt());
    }

    @GetMapping("/quote")
    public SubscriptionQuoteResponse quote(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @RequestParam("plan") SubscriptionPlan plan) {
        TenantAccess.requireProviderUser(user);
        var intent = quoteService.createQuote(user.getProviderId(), plan);
        return new SubscriptionQuoteResponse(
                intent.getId(),
                intent.getAmountDue(),
                intent.getPaymentReference(),
                intent.getBaseMonthly(),
                intent.getUsageFeePercent(),
                intent.getPaidTransactionsCount(),
                intent.getPaidOrderTotalsSum(),
                intent.getUsageFeesTotal(),
                intent.getUsageWindowStart());
    }

    @PostMapping("/select")
    public ProviderSubscriptionStatusResponse select(
            @AuthenticationPrincipal MarketUserPrincipal user, @Valid @RequestBody SelectSubscriptionRequest req) {
        TenantAccess.requireProviderUser(user);
        var s = subscriptionService.selectPlan(user.getProviderId(), req.plan(), req.billingCycle());
        boolean valid = s.getStatus() == com.agrimarket.domain.SubscriptionStatus.ACTIVE
                && s.getExpiresAt() != null
                && s.getExpiresAt().isAfter(Instant.now());
        return new ProviderSubscriptionStatusResponse(
                valid,
                s.getPlan(),
                s.getBillingCycle(),
                s.getStatus(),
                s.getExpiresAt(),
                s.getAmountDue(),
                s.getPaymentReference());
    }
}

