package com.agrimarket.security;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.UserRole;
import com.agrimarket.service.ProviderPermissionService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Central place for the ownership / role questions controllers ask before returning or changing data:
 * is the caller a provider user at all, may they write (not a read-only viewer, holds the right permission key),
 * are they a provider manager (owner or provider admin), and does a loaded record belong to their provider?
 *
 * <p>Usable both imperatively ({@code require*}) and from {@code @PreAuthorize("@accessGuard.xxx(principal)")}.
 * Records owned by another provider are reported as 404 so ids of other tenants' data are not confirmed.
 */
@Component("accessGuard")
@RequiredArgsConstructor
public class AccessGuard {

    /** {@code @PreAuthorize} expressions (keep the bean name {@code accessGuard} in sync). */
    public static final String PROVIDER_USER = "@accessGuard.allowsProviderUser(principal)";

    public static final String PROVIDER_MANAGER = "@accessGuard.allowsProviderManager(principal)";

    public static final String LISTING_WRITE = "@accessGuard.allowsListingWrite(principal)";

    /** Any of these lets a provider user view orders / rental bookings. */
    private static final Set<ProviderPermissionKey> ORDER_READ_KEYS = Set.of(
            ProviderPermissionKey.ORDERS_READ,
            ProviderPermissionKey.ORDERS_WRITE,
            ProviderPermissionKey.RENTALS_READ,
            ProviderPermissionKey.RENTALS_WRITE);

    /** Any of these lets a provider user change orders / rental bookings. */
    private static final Set<ProviderPermissionKey> ORDER_WRITE_KEYS =
            Set.of(ProviderPermissionKey.ORDERS_WRITE, ProviderPermissionKey.RENTALS_WRITE);

    private final ProviderPermissionService providerPermissionService;

    // ---------------------------------------------------------------- boolean checks (for @PreAuthorize)

    /** Accepts {@code Object} so {@code principal} (a String for anonymous callers) can be passed from SpEL. */
    public boolean allowsProviderUser(Object principal) {
        return principal instanceof MarketUserPrincipal p && isProviderUser(p);
    }

    public boolean allowsProviderManager(Object principal) {
        return principal instanceof MarketUserPrincipal p && isProviderManager(p);
    }

    public boolean allowsListingWrite(Object principal) {
        return principal instanceof MarketUserPrincipal p && canWriteListings(p);
    }

    public boolean isProviderUser(MarketUserPrincipal p) {
        return p != null && p.getProviderId() != null && isProviderRole(p.getRole());
    }

    /** Provider owner or provider admin (settings, banking, destructive bulk actions). */
    public boolean isProviderManager(MarketUserPrincipal p) {
        return isProviderUser(p) && (p.getRole() == UserRole.PROVIDER_OWNER || p.getRole() == UserRole.PROVIDER_ADMIN);
    }

    public boolean hasAnyProviderPermission(MarketUserPrincipal p, Set<ProviderPermissionKey> keys) {
        if (!isProviderUser(p)) {
            return false;
        }
        if (p.getRole() == UserRole.PROVIDER_OWNER) {
            return true;
        }
        Set<ProviderPermissionKey> mine = providerPermissionService.effectivePermissions(p);
        return keys.stream().anyMatch(mine::contains);
    }

    /** Writes: never for the read-only viewer role, otherwise one of the given keys. */
    public boolean canProviderWrite(MarketUserPrincipal p, Set<ProviderPermissionKey> keys) {
        return isProviderUser(p) && p.getRole() != UserRole.PROVIDER_VIEWER && hasAnyProviderPermission(p, keys);
    }

    public boolean canReadOrders(MarketUserPrincipal p) {
        return hasAnyProviderPermission(p, ORDER_READ_KEYS);
    }

    public boolean canWriteOrders(MarketUserPrincipal p) {
        return canProviderWrite(p, ORDER_WRITE_KEYS);
    }

    public boolean canWriteListings(MarketUserPrincipal p) {
        return canProviderWrite(p, Set.of(ProviderPermissionKey.LISTINGS_WRITE));
    }

    // ---------------------------------------------------------------- throwing variants

    public MarketUserPrincipal requireProviderUser(MarketUserPrincipal p) {
        if (p == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        if (!isProviderUser(p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT", "Not a provider user");
        }
        return p;
    }

    public void requireProviderManager(MarketUserPrincipal p) {
        requireProviderUser(p);
        if (!isProviderManager(p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE", "Only the provider owner or admin can do this");
        }
    }

    public void requireOrderRead(MarketUserPrincipal p) {
        requireProviderUser(p);
        if (!canReadOrders(p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PERMISSION", "Insufficient permissions");
        }
    }

    public void requireOrderWrite(MarketUserPrincipal p) {
        requireProviderUser(p);
        if (!canWriteOrders(p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PERMISSION", "Insufficient permissions");
        }
    }

    public void requireListingWrite(MarketUserPrincipal p) {
        requireProviderUser(p);
        if (!canWriteListings(p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PERMISSION", "Insufficient permissions");
        }
    }

    // ---------------------------------------------------------------- ownership

    /** True when the record's provider is the caller's provider. */
    public static boolean ownedBy(Provider owner, Long providerId) {
        return owner != null && owner.getId() != null && providerId != null && owner.getId().equals(providerId);
    }

    /**
     * Throws 404 when the record's provider is not {@code providerId}. Other tenants' records are reported as
     * missing (not 403) so that probing ids does not reveal which exist.
     */
    public static void requireOwnedBy(Provider owner, Long providerId, String code, String notFoundMessage) {
        if (!ownedBy(owner, providerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, code, notFoundMessage);
        }
    }

    private static boolean isProviderRole(UserRole role) {
        return role == UserRole.PROVIDER_OWNER
                || role == UserRole.PROVIDER_ADMIN
                || role == UserRole.PROVIDER_STAFF
                || role == UserRole.PROVIDER_VIEWER;
    }
}
