package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ProviderRepository providerRepository;
    private final SubscriptionUsageBilling usageBilling;

    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(Long providerId) {
        return !subscriptionRepository
                .findActiveForProviderOrderByExpiresAtDesc(providerId, SubscriptionStatus.ACTIVE, Instant.now())
                .isEmpty();
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> currentActive(Long providerId) {
        return subscriptionRepository
                .findActiveForProviderOrderByExpiresAtDesc(providerId, SubscriptionStatus.ACTIVE, Instant.now())
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> currentLatest(Long providerId) {
        return subscriptionRepository.findTopByProviderIdOrderByCreatedAtDesc(providerId);
    }

    /**
     * Status for UI + gates: prefer a non-expired ACTIVE row; otherwise fall back to the latest row
     * (e.g. pending verification).
     */
    @Transactional(readOnly = true)
    public SubscriptionStatusSnapshot resolveStatusSnapshot(Long providerId) {
        Optional<Subscription> active = currentActive(providerId);
        if (active.isPresent()) {
            return new SubscriptionStatusSnapshot(true, active.get());
        }
        Optional<Subscription> latest = currentLatest(providerId);
        if (latest.isEmpty()) {
            return new SubscriptionStatusSnapshot(false, null);
        }
        Subscription cur = latest.get();
        boolean valid = cur.getStatus() == SubscriptionStatus.ACTIVE
                && cur.getExpiresAt() != null
                && cur.getExpiresAt().isAfter(Instant.now());
        return new SubscriptionStatusSnapshot(valid, cur);
    }

    public record SubscriptionStatusSnapshot(boolean valid, Subscription subscription) {}

    @Transactional
    public Subscription selectPlan(Long providerId, SubscriptionPlan plan, BillingCycle billingCycle) {
        if (billingCycle != BillingCycle.MONTHLY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION", "Only monthly subscriptions are supported");
        }
        Provider p = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));

        // Cancel any existing active rows (keep history).
        subscriptionRepository
                .findActiveForProviderOrderByExpiresAtDesc(providerId, SubscriptionStatus.ACTIVE, Instant.now())
                .forEach(existing -> existing.setStatus(SubscriptionStatus.CANCELLED));

        Subscription s = new Subscription();
        s.setProvider(p);
        s.setPlan(plan);
        s.setBillingCycle(billingCycle);
        s.setStatus(SubscriptionStatus.PENDING_VERIFICATION);

        Instant now = Instant.now();
        // Column is non-null in DB; treat as the end of the current billing cycle even before approval.
        s.setExpiresAt(now.plus(30, ChronoUnit.DAYS));
        s.setCreatedAt(now);
        s.setPaymentReference(generatePaymentReference(p.getId()));
        var invoice = usageBilling.computeFirstMonthInvoice(providerId, plan, now);
        s.setAmountDue(invoice.totalDue());
        return subscriptionRepository.save(s);
    }

    private String generatePaymentReference(Long providerId) {
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return "SUB-" + providerId + "-" + shortId;
    }

    @Transactional
    Subscription activateFromVerifiedPeachCallback(Subscription s) {
        if (s == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION", "Subscription not found");
        }
        if (s.getStatus() != SubscriptionStatus.PENDING_VERIFICATION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION", "Subscription is not pending verification");
        }
        Instant now = Instant.now();
        Instant exp = s.getBillingCycle() == BillingCycle.YEARLY
                ? now.plus(365, ChronoUnit.DAYS)
                : now.plus(30, ChronoUnit.DAYS);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setExpiresAt(exp);
        activateProviderForMarketplace(s.getProvider());
        return subscriptionRepository.save(s);
    }

    private void activateProviderForMarketplace(Provider provider) {
        if (provider == null || provider.getStatus() == ProviderStatus.SUSPENDED) {
            return;
        }
        if (provider.getStatus() != ProviderStatus.ACTIVE) {
            provider.setStatus(ProviderStatus.ACTIVE);
            providerRepository.save(provider);
        }
    }
}
