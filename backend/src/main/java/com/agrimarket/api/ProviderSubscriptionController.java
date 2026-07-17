package com.agrimarket.api;

import com.agrimarket.api.dto.PeachCheckoutResponse;
import com.agrimarket.api.dto.PeachSubscriptionCheckoutRequest;
import com.agrimarket.api.dto.ProviderSubscriptionStatusResponse;
import com.agrimarket.api.dto.SubscriptionQuoteResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.PeachPaymentService;
import com.agrimarket.service.SubscriptionQuoteService;
import com.agrimarket.service.SubscriptionService;
import com.agrimarket.service.TenantAccess;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.agrimarket.domain.SubscriptionPlan;

@RestController
@RequestMapping("/api/provider/me/subscription")
@RequiredArgsConstructor
public class ProviderSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionQuoteService quoteService;
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
    public void bankDetails(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        throw retiredManualPaymentRoute();
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
    public void uploadProof(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        throw retiredManualPaymentRoute();
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
    public void select(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        throw retiredManualPaymentRoute();
    }

    private static ApiException retiredManualPaymentRoute() {
        return new ApiException(
                HttpStatus.GONE,
                "SUBSCRIPTION_PAYMENT_RETIRED",
                "Provider subscriptions must be paid through Peach Hosted Checkout.");
    }
}

