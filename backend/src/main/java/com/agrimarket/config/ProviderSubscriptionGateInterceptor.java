package com.agrimarket.config;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.SubscriptionService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ProviderSubscriptionGateInterceptor implements HandlerInterceptor {

    private final SubscriptionService subscriptionService;

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
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof MarketUserPrincipal p)) {
            return true;
        }
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
        return true;
    }
}

