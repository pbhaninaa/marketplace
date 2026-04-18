package com.agrimarket.service;

import com.agrimarket.api.dto.ProviderRegisterRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderSubtype;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SubscriptionRepository;
import com.agrimarket.repo.UserAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderRegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final ProviderRepository providerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SlugService slugService;

    @Transactional
    public void register(ProviderRegisterRequest req) {
        if (userAccountRepository.findByEmailIgnoreCase(req.ownerEmail()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already registered");
        }
        Provider p = new Provider(
                req.businessName(),
                slugService.uniqueProviderSlug(req.businessName()),
                req.description(),
                req.location());
        p.setSubtype(req.subtype() != null ? req.subtype() : ProviderSubtype.RESELLER);
        p.setStatus(ProviderStatus.PENDING);
        providerRepository.save(p);

        UserAccount owner = new UserAccount(req.ownerEmail(), passwordEncoder.encode(req.password()), UserRole.PROVIDER_OWNER, p);
        userAccountRepository.save(owner);

        Subscription sub = new Subscription();
        sub.setProvider(p);
        sub.setPlan(SubscriptionPlan.BASIC);
        sub.setBillingCycle(BillingCycle.MONTHLY);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        subscriptionRepository.save(sub);
    }
}
