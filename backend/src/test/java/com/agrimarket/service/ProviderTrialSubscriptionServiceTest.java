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

class ProviderTrialSubscriptionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void hasActiveSubscription_trueDuringLiveTrialWithoutPaidRow() {
        Provider p = new Provider("Trial Farm", "trial-farm", "desc", "loc");
        Instant start = Instant.now().minus(5, ChronoUnit.DAYS);
        p.setCreatedAt(start);
        ProviderTrialSupport.assignTrialOnce(p, start);
        providerRepository.save(p);

        assertThat(subscriptionService.hasActiveSubscription(p.getId())).isTrue();
        var snapshot = subscriptionService.resolveStatusSnapshot(p.getId());
        assertThat(snapshot.valid()).isTrue();
        assertThat(snapshot.onTrial()).isTrue();
        assertThat(snapshot.subscription()).isNull();
        assertThat(snapshot.trialDaysRemaining()).isGreaterThan(0);
    }

    @Test
    void hasActiveSubscription_falseWhenTrialExpiredAndNoPaidRow() {
        Provider p = new Provider("Expired Trial Farm", "expired-trial-farm", "desc", "loc");
        Instant start = Instant.now().minus(45, ChronoUnit.DAYS);
        p.setCreatedAt(start);
        ProviderTrialSupport.assignTrialOnce(p, start);
        providerRepository.save(p);

        assertThat(subscriptionService.hasActiveSubscription(p.getId())).isFalse();
        var snapshot = subscriptionService.resolveStatusSnapshot(p.getId());
        assertThat(snapshot.valid()).isFalse();
        assertThat(snapshot.onTrial()).isFalse();
    }

    @Test
    void paidActiveSubscription_takesPrecedenceOverTrialFlags() {
        Provider p = new Provider("Paid Farm", "paid-farm", "desc", "loc");
        Instant start = Instant.now().minus(2, ChronoUnit.DAYS);
        ProviderTrialSupport.assignTrialOnce(p, start);
        providerRepository.save(p);

        Subscription active = new Subscription();
        active.setProvider(p);
        active.setPlan(SubscriptionPlan.PREMIUM);
        active.setBillingCycle(BillingCycle.MONTHLY);
        active.setStatus(SubscriptionStatus.ACTIVE);
        active.setExpiresAt(Instant.now().plus(20, ChronoUnit.DAYS));
        active.setCreatedAt(Instant.now());
        subscriptionRepository.save(active);

        assertThat(subscriptionService.hasActiveSubscription(p.getId())).isTrue();
        var snapshot = subscriptionService.resolveStatusSnapshot(p.getId());
        assertThat(snapshot.valid()).isTrue();
        assertThat(snapshot.onTrial()).isFalse();
        assertThat(snapshot.subscription().getPlan()).isEqualTo(SubscriptionPlan.PREMIUM);
    }

    @Test
    void assignTrialOnce_doesNotResetOnSubsequentSave() {
        Provider p = new Provider("Sticky Trial", "sticky-trial", "desc", "loc");
        Instant start = Instant.parse("2026-01-10T08:00:00Z");
        ProviderTrialSupport.assignTrialOnce(p, start);
        providerRepository.save(p);

        Provider loaded = providerRepository.findById(p.getId()).orElseThrow();
        loaded.setName("Sticky Trial Renamed");
        ProviderTrialSupport.assignTrialOnce(loaded, Instant.now());
        providerRepository.save(loaded);

        Provider again = providerRepository.findById(p.getId()).orElseThrow();
        assertThat(again.getTrialStartedAt()).isEqualTo(start);
        assertThat(again.getTrialEndsAt()).isEqualTo(start.plus(30, ChronoUnit.DAYS));
        assertThat(again.getName()).isEqualTo("Sticky Trial Renamed");
    }
}
