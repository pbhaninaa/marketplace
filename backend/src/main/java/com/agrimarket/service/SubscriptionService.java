package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ProviderRepository providerRepository;

    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(Long providerId) {
        return subscriptionRepository
                .findTopActiveForProvider(providerId, SubscriptionStatus.ACTIVE, Instant.now())
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> currentActive(Long providerId) {
        return subscriptionRepository.findTopActiveForProvider(providerId, SubscriptionStatus.ACTIVE, Instant.now());
    }

    @Transactional
    public Subscription selectPlan(Long providerId, SubscriptionPlan plan, BillingCycle billingCycle) {
        Provider p = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));

        // Cancel any existing active rows (keep history).
        subscriptionRepository
                .findTopActiveForProvider(providerId, SubscriptionStatus.ACTIVE, Instant.now())
                .ifPresent(existing -> existing.setStatus(SubscriptionStatus.CANCELLED));

        Subscription s = new Subscription();
        s.setProvider(p);
        s.setPlan(plan);
        s.setBillingCycle(billingCycle);
        s.setStatus(SubscriptionStatus.ACTIVE);

        Instant now = Instant.now();
        Instant exp = billingCycle == BillingCycle.YEARLY ? now.plus(365, ChronoUnit.DAYS) : now.plus(30, ChronoUnit.DAYS);
        s.setExpiresAt(exp);
        s.setCreatedAt(now);
        return subscriptionRepository.save(s);
    }
}
