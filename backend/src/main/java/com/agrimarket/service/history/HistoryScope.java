package com.agrimarket.service.history;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.security.MarketUserPrincipal;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Who is asking — decides which rows are visible regardless of the filters sent by the browser.
 *
 * @param providerId when set, only that provider's rows (provider users)
 * @param clientTarget when set, only rows whose customer email/phone matches (verified client)
 * @param clientView client-facing rows (include the client's own order reference)
 */
public record HistoryScope(Long providerId, String clientTarget, boolean clientView) {

    public static HistoryScope admin() {
        return new HistoryScope(null, null, false);
    }

    public static HistoryScope provider(MarketUserPrincipal actor) {
        if (actor == null || actor.getProviderId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT", "Not a provider user");
        }
        return new HistoryScope(actor.getProviderId(), null, false);
    }

    public static HistoryScope client(String verifiedTarget) {
        if (verifiedTarget == null || verifiedTarget.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Sign in with your email code first");
        }
        return new HistoryScope(null, verifiedTarget.trim().toLowerCase(Locale.ROOT), true);
    }

    /** Applies the scope on top of browser-supplied filters (a provider can never widen to another provider). */
    public HistoryFilter apply(HistoryFilter f) {
        return providerId != null ? f.withProvider(providerId) : f;
    }
}
