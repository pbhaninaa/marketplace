package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderAccountService {

    private final ProviderRepository providerRepository;
    private final UserAccountRepository userAccountRepository;
    private final ListingRepository listingRepository;

    @Transactional
    public void deactivateProviderAccount(MarketUserPrincipal actor) {
        TenantAccess.requireProviderUser(actor);
        if (actor.getRole() != UserRole.PROVIDER_OWNER && actor.getRole() != UserRole.PROVIDER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE", "Only provider owner/admin can deactivate this account.");
        }
        var provider = providerRepository
                .findById(actor.getProviderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));

        provider.setStatus(ProviderStatus.SUSPENDED);
        providerRepository.save(provider);

        // Disable all users in this provider (including owner) to prevent login.
        for (var u : userAccountRepository.findByProvider_IdOrderByEmailAsc(provider.getId())) {
            u.setEnabled(false);
            userAccountRepository.save(u);
        }

        // Unpublish listings (keep rows for order history; provider can be reactivated by admin later).
        for (var l : listingRepository.findAllByProvider_Id(provider.getId())) {
            l.setActive(false);
            listingRepository.save(l);
        }
    }
}

