package com.agrimarket.config;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.SubscriptionService;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ProviderSubscriptionGateInterceptor implements HandlerInterceptor {

    private final SubscriptionService subscriptionService;

    public ProviderSubscriptionGateInterceptor(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public boolean preHandle(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            Object handler) {

        String uri = request.getRequestURI();
        if (uri == null) return true;

        // Only gate provider-me endpoints (UI relies on these).
        if (!uri.startsWith("/api/provider/me")) return true;

        // Allow subscription endpoints always.
        if (uri.startsWith("/api/provider/me/subscription")) return true;

        // If unauthenticated, let normal security handle it.
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof MarketUserPrincipal)) {
            return true;
        }
        MarketUserPrincipal p = (MarketUserPrincipal) authentication.getPrincipal();
        if (p.getProviderId() == null) {
            return true;
        }

        var active = subscriptionService.currentActive(p.getProviderId()).orElse(null);
        boolean valid = active != null && active.getExpiresAt() != null && active.getExpiresAt().isAfter(Instant.now());
        if (!valid) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "SUBSCRIPTION_INACTIVE",
                    "Subscription required. Please choose a plan to continue.");
        }

        // Plan feature gate (Premium-only provider tools).
        if (requiresPremium(uri)) {
            SubscriptionPlan plan = active.getPlan() == null ? SubscriptionPlan.BASIC : active.getPlan();
            if (plan != SubscriptionPlan.PREMIUM) {
                throw new ApiException(
                        HttpStatus.PAYMENT_REQUIRED,
                        "PLAN_UPGRADE_REQUIRED",
                        "Your current plan doesn't include this feature. Upgrade to Premium to continue.");
            }
        }
        return true;
    }

    private static boolean requiresPremium(String uri) {
        if (uri == null) return false;
        // Premium provider tools (team management + payroll).
        return uri.startsWith("/api/provider/me/staff")
                || uri.startsWith("/api/provider/me/payroll-entries")
                || uri.contains("/payroll");
    }
}

