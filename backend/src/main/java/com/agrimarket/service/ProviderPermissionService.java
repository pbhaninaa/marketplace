package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.ProviderSubtype;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.ProviderStaffPermissionRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderPermissionService {

    private final ProviderRepository providerRepository;
    private final ProviderStaffPermissionRepository providerStaffPermissionRepository;

    @Transactional(readOnly = true)
    public Provider loadProviderOrThrow(Long providerId) {
        return providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
    }

    @Transactional(readOnly = true)
    public ProviderSubtype providerSubtype(Long providerId) {
        return loadProviderOrThrow(providerId).getSubtype();
    }

    public Set<ProviderPermissionKey> applicableKeys(ProviderSubtype subtype) {
        EnumSet<ProviderPermissionKey> out = EnumSet.noneOf(ProviderPermissionKey.class);
        for (ProviderPermissionKey k : ProviderPermissionKey.values()) {
            if (k.applicableTo().contains(subtype)) {
                out.add(k);
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Set<ProviderPermissionKey> effectivePermissions(MarketUserPrincipal actor) {
        if (actor == null || actor.getProviderId() == null) {
            return Set.of();
        }
        if (actor.getRole() == UserRole.PROVIDER_OWNER) {
            ProviderSubtype subtype = providerSubtype(actor.getProviderId());
            return applicableKeys(subtype);
        }
        List<ProviderPermissionKey> keys =
                providerStaffPermissionRepository.findKeys(actor.getProviderId(), actor.getUserId());
        ProviderSubtype subtype = providerSubtype(actor.getProviderId());
        Set<ProviderPermissionKey> applicable = applicableKeys(subtype);
        EnumSet<ProviderPermissionKey> filtered = EnumSet.noneOf(ProviderPermissionKey.class);
        for (ProviderPermissionKey k : keys) {
            if (applicable.contains(k)) {
                filtered.add(k);
            }
        }
        return filtered;
    }

    public void require(MarketUserPrincipal actor, ProviderPermissionKey key) {
        if (actor == null || actor.getProviderId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT", "Not a provider user");
        }
        if (actor.getRole() == UserRole.PROVIDER_OWNER) {
            return;
        }
        Set<ProviderPermissionKey> keys = effectivePermissions(actor);
        if (!keys.contains(key)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PERMISSION", "Insufficient permissions");
        }
    }

    public void requireManageTeam(MarketUserPrincipal actor) {
        if (actor != null && actor.getRole() == UserRole.PROVIDER_OWNER) return;
        require(actor, ProviderPermissionKey.TEAM_MANAGE);
    }

    public boolean canGrant(MarketUserPrincipal granter, Set<ProviderPermissionKey> requested) {
        if (granter != null && granter.getRole() == UserRole.PROVIDER_OWNER) {
            return true;
        }
        Set<ProviderPermissionKey> own = effectivePermissions(granter);
        return own.containsAll(requested);
    }
}

