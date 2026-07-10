package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SubscriptionExpiryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SubscriptionExpiryService subscriptionExpiryService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Test
    void expireLapsedSubscriptions_marksPastActiveAsExpired() {
        Provider p = providerRepository.save(new Provider("Expiry Farm", "expiry-farm", "desc", "loc"));

        Subscription activePast = new Subscription();
        activePast.setProvider(p);
        activePast.setPlan(SubscriptionPlan.BASIC);
        activePast.setBillingCycle(BillingCycle.MONTHLY);
        activePast.setStatus(SubscriptionStatus.ACTIVE);
        activePast.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        activePast.setCreatedAt(Instant.now().minus(31, ChronoUnit.DAYS));
        subscriptionRepository.save(activePast);

        Subscription stillValid = new Subscription();
        stillValid.setProvider(p);
        stillValid.setPlan(SubscriptionPlan.PREMIUM);
        stillValid.setBillingCycle(BillingCycle.MONTHLY);
        stillValid.setStatus(SubscriptionStatus.ACTIVE);
        stillValid.setExpiresAt(Instant.now().plus(10, ChronoUnit.DAYS));
        stillValid.setCreatedAt(Instant.now());
        subscriptionRepository.save(stillValid);

        subscriptionExpiryService.expireLapsedSubscriptions();

        assertThat(subscriptionRepository.findById(activePast.getId()))
                .get()
                .extracting(Subscription::getStatus)
                .isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscriptionRepository.findById(stillValid.getId()))
                .get()
                .extracting(Subscription::getStatus)
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void resolveStatusSnapshot_prefersActiveOverNewerPendingRow() {
        Provider p = providerRepository.save(new Provider("Status Farm", "status-farm", "desc", "loc"));

        Subscription active = new Subscription();
        active.setProvider(p);
        active.setPlan(SubscriptionPlan.PREMIUM);
        active.setBillingCycle(BillingCycle.MONTHLY);
        active.setStatus(SubscriptionStatus.ACTIVE);
        active.setExpiresAt(Instant.now().plus(20, ChronoUnit.DAYS));
        active.setCreatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        subscriptionRepository.save(active);

        Subscription pending = new Subscription();
        pending.setProvider(p);
        pending.setPlan(SubscriptionPlan.BASIC);
        pending.setBillingCycle(BillingCycle.MONTHLY);
        pending.setStatus(SubscriptionStatus.PENDING_VERIFICATION);
        pending.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        pending.setCreatedAt(Instant.now());
        subscriptionRepository.save(pending);

        var snapshot = subscriptionService.resolveStatusSnapshot(p.getId());

        assertThat(snapshot.valid()).isTrue();
        assertThat(snapshot.subscription().getPlan()).isEqualTo(SubscriptionPlan.PREMIUM);
        assertThat(snapshot.subscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }
}
