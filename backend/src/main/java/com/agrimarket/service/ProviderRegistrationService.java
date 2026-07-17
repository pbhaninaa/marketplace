package com.agrimarket.service;

import com.agrimarket.api.dto.ProviderRegisterRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderSubtype;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers a merchant (provider) + owner account.
 * New providers receive a one-time 30-day free trial (no plan selection/payment). After the trial,
 * Peach Hosted Checkout (Card or Instant EFT) is required. Approved monthly plans run for 30 days.
 */
@Service
@RequiredArgsConstructor
public class ProviderRegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;
    private final SlugService slugService;
    private final Clock clock;

    @Transactional
    public void register(ProviderRegisterRequest req) {
        if (userAccountRepository.findByEmailIgnoreCase(req.ownerEmail()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already registered");
        }
        Instant now = Instant.now(clock);
        Provider p = new Provider(
                req.businessName(),
                slugService.uniqueProviderSlug(req.businessName()),
                req.description(),
                req.location());
        p.setSubtype(req.subtype() != null ? req.subtype() : ProviderSubtype.RESELLER);
        p.setCreatedAt(now);
        ProviderTrialSupport.assignTrialOnce(p, now);
        // Trial unlocks the provider portal and marketplace participation immediately.
        p.setStatus(ProviderStatus.ACTIVE);
        providerRepository.save(p);

        UserAccount owner = new UserAccount(req.ownerEmail(), passwordEncoder.encode(req.password()), UserRole.PROVIDER_OWNER, p);
        owner.setCreatedAt(now);
        userAccountRepository.save(owner);
    }
}
