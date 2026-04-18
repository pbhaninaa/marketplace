package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.security.MarketUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

public final class TenantAccess {

    private TenantAccess() {}

    public static MarketUserPrincipal requireProviderUser(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof MarketUserPrincipal p)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        return requireProviderUser(p);
    }

    public static MarketUserPrincipal requireProviderUser(MarketUserPrincipal p) {
        if (p == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        if (p.getProviderId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT", "Not a provider user");
        }
        return p;
    }

    /** Legacy helpers left intentionally minimal; permission-key enforcement happens in services. */
    public static void assertCanWriteListings(MarketUserPrincipal p) {
        if (p == null || p.getProviderId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT", "Not a provider user");
        }
    }

    /** Legacy helpers left intentionally minimal; permission-key enforcement happens in services. */
    public static void assertCanManageStaff(MarketUserPrincipal p) {
        if (p == null || p.getProviderId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT", "Not a provider user");
        }
    }
}
