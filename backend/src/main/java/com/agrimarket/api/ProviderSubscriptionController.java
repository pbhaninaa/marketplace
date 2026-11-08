package com.agrimarket.api;

import com.agrimarket.api.dto.PeachCheckoutResponse;
import com.agrimarket.api.dto.PeachSubscriptionCheckoutRequest;
import com.agrimarket.api.dto.ProviderSubscriptionStatusResponse;
import com.agrimarket.api.dto.SubscriptionQuoteResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.api.dto.OnlineCheckoutResponse;
import com.agrimarket.domain.PaymentGateway;
import com.agrimarket.service.OnlinePaymentGatewayResolver;
import com.agrimarket.service.PayFastPaymentService;
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
    private final PayFastPaymentService payFastPaymentService;
    private final OnlinePaymentGatewayResolver gatewayResolver;

    @GetMapping("/status")
    public ProviderSubscriptionStatusResponse status(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        var snapshot = subscriptionService.resolveStatusSnapshot(user.getProviderId());
        var cur = snapshot.subscription();
        if (cur == null && !snapshot.onTrial()) {
            return new ProviderSubscriptionStatusResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    snapshot.trialStartedAt(),
                    snapshot.trialEndsAt(),
                    snapshot.trialDaysRemaining());
        }
        if (cur == null) {
            // Live free trial with no paid subscription row yet.
            return new ProviderSubscriptionStatusResponse(
                    true,
                    null,
                    null,
                    null,
                    snapshot.trialEndsAt(),
                    null,
                    null,
                    true,
                    snapshot.trialStartedAt(),
                    snapshot.trialEndsAt(),
                    snapshot.trialDaysRemaining());
        }
        return new ProviderSubscriptionStatusResponse(
                snapshot.valid(),
                cur.getPlan(),
                cur.getBillingCycle(),
                cur.getStatus(),
                snapshot.onTrial() ? snapshot.trialEndsAt() : cur.getExpiresAt(),
                cur.getAmountDue(),
                cur.getPaymentReference(),
                snapshot.onTrial(),
                snapshot.trialStartedAt(),
                snapshot.trialEndsAt(),
                snapshot.trialDaysRemaining());
    }

    @GetMapping("/bank-details")
    public void bankDetails(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        throw retiredManualPaymentRoute();
    }

    @GetMapping("/peach-configured")
    public Map<String, Boolean> peachConfigured() {
        // "Is the online option available" (whichever gateway serves it); kept for older clients.
        return Map.of("configured", gatewayResolver.isOnlineAvailable());
    }

    /** Starts an online plan payment through the active gateway (Peach redirect or PayFast form). */
    @PostMapping("/online-checkout")
    public OnlineCheckoutResponse onlineCheckout(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @jakarta.validation.Valid @RequestBody PeachSubscriptionCheckoutRequest req) {
        TenantAccess.requireProviderUser(user);
        PaymentGateway gateway = gatewayResolver.requireActiveGateway();
        if (gateway == PaymentGateway.PAYFAST) {
            var payfast = payFastPaymentService.initiateSubscriptionCheckout(
                    user.getProviderId(), req.intentId(), req.peachPaymentMethod(), user.getUsername());
            return new OnlineCheckoutResponse(gateway.name(), payfast.paymentRef(), null, payfast);
        }
        var peach = peachPaymentService.initiateSubscriptionCheckout(
                user.getProviderId(), req.intentId(), req.peachPaymentMethod());
        return new OnlineCheckoutResponse(gateway.name(), peach.merchantTransactionId(), peach.redirectUrl(), null);
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
