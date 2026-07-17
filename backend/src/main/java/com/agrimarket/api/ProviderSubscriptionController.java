package com.agrimarket.api;

import com.agrimarket.api.dto.PeachCheckoutResponse;
import com.agrimarket.api.dto.PeachSubscriptionCheckoutRequest;
import com.agrimarket.api.dto.ProviderSubscriptionStatusResponse;
import com.agrimarket.api.dto.BankDetailsResponse;
import com.agrimarket.api.dto.SelectSubscriptionRequest;
import com.agrimarket.api.dto.SubscriptionQuoteResponse;
import com.agrimarket.api.dto.UploadProofResponse;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.PeachPaymentService;
import com.agrimarket.service.SubscriptionPaymentProofService;
import com.agrimarket.service.SubscriptionQuoteService;
import com.agrimarket.service.SubscriptionService;
import com.agrimarket.service.TenantAccess;
import com.agrimarket.service.PlatformSettingsService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
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
    private final PeachPaymentService peachPaymentService;

    @GetMapping("/status")
    public ProviderSubscriptionStatusResponse status(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        var snapshot = subscriptionService.resolveStatusSnapshot(user.getProviderId());
        var cur = snapshot.subscription();
        if (cur == null) {
            return new ProviderSubscriptionStatusResponse(false, null, null, null, null, null, null);
        }
        return new ProviderSubscriptionStatusResponse(
                snapshot.valid(),
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

    @GetMapping("/peach-configured")
    public Map<String, Boolean> peachConfigured() {
        return Map.of("configured", peachPaymentService.isConfigured());
    }

    @PostMapping("/peach-checkout")
    public PeachCheckoutResponse peachCheckout(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @jakarta.validation.Valid @RequestBody PeachSubscriptionCheckoutRequest req) {
        TenantAccess.requireProviderUser(user);
        return peachPaymentService.initiateSubscriptionCheckout(
                user.getProviderId(), req.intentId(), req.peachPaymentMethod());
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

