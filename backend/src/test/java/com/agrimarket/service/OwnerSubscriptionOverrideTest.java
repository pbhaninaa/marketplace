package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.AppNotificationType;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionOverrideAction;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.AppNotificationRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SubscriptionRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Platform-owner overrides: extend/end a provider's trial and set a subscription without Peach. */
class OwnerSubscriptionOverrideTest extends AbstractIntegrationTest {

    @Autowired private OwnerSubscriptionOverrideService service;
    @Autowired private SubscriptionService subscriptionService;
    @Autowired private SubscriptionExpiryService expiryService;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private AppNotificationRepository notificationRepository;

    private Provider provider;
    private UserAccount providerOwner;
    private MarketUserPrincipal actor;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        provider = new Provider("Override Farm " + suffix, "override-farm-" + suffix, "desc", "loc");
        provider.setStatus(ProviderStatus.PENDING);
        providerRepository.save(provider);
        providerOwner = userAccountRepository.save(
                new UserAccount("owner-" + suffix + "@farm.test", "x", UserRole.PROVIDER_OWNER, provider));
        UserAccount admin = userAccountRepository.save(
                new UserAccount("founder-" + suffix + "@admin.test", "x", UserRole.PLATFORM_ADMIN, null));
        actor = new MarketUserPrincipal(admin);
    }

    private void trial(Instant start) {
        ProviderTrialSupport.assignTrialOnce(provider, start);
        providerRepository.save(provider);
    }

    @Test
    void extendsARunningTrialFromItsCurrentEnd() {
        trial(Instant.now().minus(28, ChronoUnit.DAYS));
        Instant oldEnd = provider.getTrialEndsAt();
        Instant oldStart = provider.getTrialStartedAt();

        service.extendTrial(provider.getId(), 10, "Test provider needs more time", actor);

        Provider p = providerRepository.findById(provider.getId()).orElseThrow();
        assertThat(p.getTrialEndsAt()).isEqualTo(oldEnd.plus(10, ChronoUnit.DAYS));
        assertThat(p.getTrialStartedAt()).isEqualTo(oldStart);
        assertThat(subscriptionService.resolveStatusSnapshot(p.getId()).onTrial()).isTrue();
        // Default trial length is unchanged.
        assertThat(ProviderTrialSupport.TRIAL_DAYS).isEqualTo(30);
    }

    @Test
    void runsFromNowWhenTheOldTrialEndedAndStaysOneTime() {
        Instant start = Instant.now().minus(60, ChronoUnit.DAYS);
        trial(start);
        assertThat(subscriptionService.hasActiveSubscription(provider.getId())).isFalse();

        service.extendTrial(provider.getId(), 7, "Goodwill", actor);

        Provider p = providerRepository.findById(provider.getId()).orElseThrow();
        assertThat(p.getTrialEndsAt()).isCloseTo(Instant.now().plus(7, ChronoUnit.DAYS), within(5, ChronoUnit.SECONDS));
        assertThat(p.getTrialStartedAt()).isEqualTo(start);
        assertThat(subscriptionService.hasActiveSubscription(p.getId())).isTrue();
        assertThat(subscriptionService.resolveStatusSnapshot(p.getId()).trialDaysRemaining()).isBetween(6L, 7L);

        // The one-time rule still holds: assignTrialOnce never re-issues over the owner's dates.
        Instant end = p.getTrialEndsAt();
        ProviderTrialSupport.assignTrialOnce(p, Instant.now());
        assertThat(p.getTrialStartedAt()).isEqualTo(start);
        assertThat(p.getTrialEndsAt()).isEqualTo(end);
    }

    @Test
    void rejectsSillyDayCountsAndMissingReason() {
        trial(Instant.now());
        assertThatThrownBy(() -> service.extendTrial(provider.getId(), 0, "r", actor)).hasMessageContaining("1 to 365");
        assertThatThrownBy(() -> service.extendTrial(provider.getId(), 366, "r", actor)).hasMessageContaining("1 to 365");
        assertThatThrownBy(() -> service.extendTrial(provider.getId(), 5, "  ", actor)).hasMessageContaining("reason");
        assertThatThrownBy(() -> service.endTrial(provider.getId(), null, actor)).hasMessageContaining("reason");
    }

    @Test
    void endTrialStopsItNow() {
        trial(Instant.now().minus(1, ChronoUnit.DAYS));
        assertThat(subscriptionService.hasActiveSubscription(provider.getId())).isTrue();

        service.endTrial(provider.getId(), "Abuse", actor);

        assertThat(subscriptionService.hasActiveSubscription(provider.getId())).isFalse();
        assertThat(subscriptionService.resolveStatusSnapshot(provider.getId()).onTrial()).isFalse();
        assertThatThrownBy(() -> service.endTrial(provider.getId(), "again", actor))
                .hasMessageContaining("not on a free trial");
    }

    @Test
    void setsPlanForOneMonthWhenNoDateGivenAndRecordsAuditAndNotifies() {
        trial(Instant.now().minus(45, ChronoUnit.DAYS));

        Subscription s = service.setSubscription(provider.getId(), SubscriptionPlan.PREMIUM, null, "Pilot partner", actor);

        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.getPlan()).isEqualTo(SubscriptionPlan.PREMIUM);
        assertThat(s.getAmountDue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getExpiresAt()).isCloseTo(Instant.now().plus(30, ChronoUnit.DAYS), within(5, ChronoUnit.SECONDS));
        assertThat(OwnerSubscriptionOverrideService.isOverride(s)).isTrue();

        var snap = subscriptionService.resolveStatusSnapshot(provider.getId());
        assertThat(snap.valid()).isTrue();
        assertThat(snap.subscription().getPlan()).isEqualTo(SubscriptionPlan.PREMIUM);
        assertThat(providerRepository.findById(provider.getId()).orElseThrow().getStatus()).isEqualTo(ProviderStatus.ACTIVE);

        var history = service.history(provider.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getAction()).isEqualTo(SubscriptionOverrideAction.SET_SUBSCRIPTION);
        assertThat(history.get(0).getActorEmail()).isEqualTo(actor.getEmail());
        assertThat(history.get(0).getActorUserId()).isEqualTo(actor.getUserId());
        assertThat(history.get(0).getReason()).isEqualTo("Pilot partner");
        assertThat(history.get(0).getCreatedAt()).isNotNull();

        assertThat(notificationRepository.findByUser_IdOrderByCreatedAtDesc(providerOwner.getId()))
                .anySatisfy(n -> {
                    assertThat(n.getNotificationType()).isEqualTo(AppNotificationType.SUBSCRIPTION_PROOF_DECISION);
                    assertThat(n.getTitle()).isEqualTo("Subscription updated");
                });
    }

    @Test
    void usesInclusiveActiveUntilDateAndReplacesTheCurrentPaidRow() {
        Subscription paid = new Subscription();
        paid.setProvider(provider);
        paid.setPlan(SubscriptionPlan.BASIC);
        paid.setBillingCycle(BillingCycle.MONTHLY);
        paid.setStatus(SubscriptionStatus.ACTIVE);
        paid.setExpiresAt(Instant.now().plus(3, ChronoUnit.DAYS));
        paid.setCreatedAt(Instant.now().minus(27, ChronoUnit.DAYS));
        subscriptionRepository.save(paid);

        LocalDate until = LocalDate.now(OwnerSubscriptionOverrideService.MARKET_ZONE).plusDays(90);
        Subscription s = service.setSubscription(provider.getId(), SubscriptionPlan.BASIC, until, "Comp", actor);

        assertThat(s.getExpiresAt())
                .isEqualTo(until.plusDays(1).atStartOfDay(OwnerSubscriptionOverrideService.MARKET_ZONE).toInstant());
        assertThat(subscriptionRepository.findById(paid.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(subscriptionService.currentActive(provider.getId())).get().extracting(Subscription::getId).isEqualTo(s.getId());
    }

    @Test
    void rejectsPastActiveUntilAndMissingPlan() {
        LocalDate yesterday = LocalDate.now(OwnerSubscriptionOverrideService.MARKET_ZONE).minusDays(1);
        assertThatThrownBy(() -> service.setSubscription(provider.getId(), SubscriptionPlan.BASIC, yesterday, "r", actor))
                .hasMessageContaining("today or later");
        assertThatThrownBy(() -> service.setSubscription(provider.getId(), null, null, "r", actor))
                .hasMessageContaining("plan");
    }

    @Test
    void expiryJobStillExpiresAnOverriddenSubscription() {
        Subscription s = service.setSubscription(provider.getId(), SubscriptionPlan.BASIC, null, "Short comp", actor);
        s.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        subscriptionRepository.save(s);

        expiryService.expireLapsedSubscriptions();

        assertThat(subscriptionRepository.findById(s.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscriptionService.hasActiveSubscription(provider.getId())).isFalse();
    }
}
