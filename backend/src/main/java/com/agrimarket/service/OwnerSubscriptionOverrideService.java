package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionOverrideAction;
import com.agrimarket.domain.SubscriptionOverrideAudit;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SubscriptionOverrideAuditRepository;
import com.agrimarket.repo.SubscriptionRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-owner overrides of a provider's free trial / subscription without a Peach payment
 * (test providers, comps, fixing mistakes). Every change is audited and the provider owners are
 * notified. Callers must check {@link PlatformOwnerService#requireOwner} first.
 *
 * <p>Overrides use the normal data model so the rest of the system keeps working unchanged:
 * the trial stays on {@code Provider.trialStartedAt/trialEndsAt} (never cleared, so
 * {@link ProviderTrialSupport#assignTrialOnce} still refuses to re-issue a trial), and an override
 * subscription is an ordinary ACTIVE {@link Subscription} row whose {@code expiresAt} is picked up
 * by {@link SubscriptionExpiryService}.
 */
@Service
@RequiredArgsConstructor
public class OwnerSubscriptionOverrideService {

    public static final int MAX_EXTEND_DAYS = 365;
    public static final int MAX_REASON_LENGTH = 500;
    /** Default override length when no active-until date is given (one monthly period). */
    public static final int DEFAULT_PERIOD_DAYS = 30;
    /** Payment reference prefix marking subscription rows created by an owner override. */
    public static final String OVERRIDE_REFERENCE_PREFIX = "OWNER-";

    static final ZoneId MARKET_ZONE = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(MARKET_ZONE);

    private final ProviderRepository providerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionOverrideAuditRepository auditRepository;
    private final AppNotificationService appNotificationService;
    private final Clock clock;

    /**
     * Adds {@code days} to a running trial, or (when the trial has ended / was never set) runs a
     * trial from now for {@code days}. The original trial start is kept, so this remains the
     * provider's single trial rather than a newly issued one.
     */
    @Transactional
    public Provider extendTrial(Long providerId, int days, String reason, MarketUserPrincipal actor) {
        if (days < 1 || days > MAX_EXTEND_DAYS) {
            throw badRequest("Extend the trial by 1 to " + MAX_EXTEND_DAYS + " days");
        }
        String why = requireReason(reason);
        Provider provider = loadProvider(providerId);
        Instant now = Instant.now(clock);
        Instant oldEnd = provider.getTrialEndsAt();
        boolean running = ProviderTrialSupport.isOnTrial(provider, now);
        Instant base = running ? oldEnd : now;
        if (provider.getTrialStartedAt() == null) {
            provider.setTrialStartedAt(now);
        }
        Instant newEnd = base.plus(days, ChronoUnit.DAYS);
        provider.setTrialEndsAt(newEnd);
        providerRepository.save(provider);

        audit(provider, actor, SubscriptionOverrideAction.EXTEND_TRIAL, why,
                "+" + days + " day(s); trial end " + fmt(oldEnd) + " -> " + fmt(newEnd)
                        + (running ? "" : " (trial was not running; restarted from now)"));
        appNotificationService.notifySubscriptionOverride(
                provider,
                "Free trial extended",
                "Your free trial has been extended by " + days + " day(s) and now ends on " + DATE_FMT.format(newEnd) + ".");
        return provider;
    }

    /** Ends a running trial now. */
    @Transactional
    public Provider endTrial(Long providerId, String reason, MarketUserPrincipal actor) {
        String why = requireReason(reason);
        Provider provider = loadProvider(providerId);
        Instant now = Instant.now(clock);
        if (!ProviderTrialSupport.isOnTrial(provider, now)) {
            throw badRequest("This provider is not on a free trial");
        }
        Instant oldEnd = provider.getTrialEndsAt();
        provider.setTrialEndsAt(now);
        providerRepository.save(provider);

        audit(provider, actor, SubscriptionOverrideAction.END_TRIAL, why,
                "trial end " + fmt(oldEnd) + " -> " + fmt(now));
        appNotificationService.notifySubscriptionOverride(
                provider,
                "Free trial ended",
                "Your free trial has been ended by the platform. Choose a plan to keep using provider features.");
        return provider;
    }

    /**
     * Sets the provider's plan and active-until date without a payment: cancels current ACTIVE rows
     * (history kept) and creates a new ACTIVE row with amount due 0. {@code activeUntil} is
     * inclusive (active through the end of that day, Africa/Johannesburg); when null, the
     * subscription runs {@value #DEFAULT_PERIOD_DAYS} days from now.
     */
    @Transactional
    public Subscription setSubscription(
            Long providerId, SubscriptionPlan plan, LocalDate activeUntil, String reason, MarketUserPrincipal actor) {
        if (plan == null) {
            throw badRequest("Choose a plan (BASIC or PREMIUM)");
        }
        String why = requireReason(reason);
        Provider provider = loadProvider(providerId);
        Instant now = Instant.now(clock);
        Instant expiresAt = activeUntil != null
                ? activeUntil.plusDays(1).atStartOfDay(MARKET_ZONE).toInstant()
                : now.plus(DEFAULT_PERIOD_DAYS, ChronoUnit.DAYS);
        if (!expiresAt.isAfter(now)) {
            throw badRequest("Active-until date must be today or later");
        }

        List<Subscription> current =
                subscriptionRepository.findActiveForProviderOrderByExpiresAtDesc(providerId, SubscriptionStatus.ACTIVE, now);
        String before = current.isEmpty()
                ? "none"
                : current.get(0).getPlan() + " until " + fmt(current.get(0).getExpiresAt());
        current.forEach(existing -> existing.setStatus(SubscriptionStatus.CANCELLED));
        subscriptionRepository.saveAll(current);

        Subscription s = new Subscription();
        s.setProvider(provider);
        s.setPlan(plan);
        s.setBillingCycle(BillingCycle.MONTHLY);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setExpiresAt(expiresAt);
        s.setCreatedAt(now);
        s.setAmountDue(BigDecimal.ZERO);
        s.setPaymentReference(OVERRIDE_REFERENCE_PREFIX + providerId + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        Subscription saved = subscriptionRepository.save(s);
        activateProviderForMarketplace(provider);

        audit(provider, actor, SubscriptionOverrideAction.SET_SUBSCRIPTION, why,
                "before: " + before + "; after: " + plan + " until " + fmt(expiresAt) + " (no payment)");
        appNotificationService.notifySubscriptionOverride(
                provider,
                "Subscription updated",
                "Your " + label(plan) + " subscription is active until " + DATE_FMT.format(expiresAt.minusSeconds(1)) + ".");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionOverrideAudit> history(Long providerId) {
        return auditRepository.findByProvider_IdOrderByCreatedAtDescIdDesc(providerId);
    }

    public static boolean isOverride(Subscription s) {
        return s != null && s.getPaymentReference() != null && s.getPaymentReference().startsWith(OVERRIDE_REFERENCE_PREFIX);
    }

    private void audit(
            Provider provider, MarketUserPrincipal actor, SubscriptionOverrideAction action, String reason, String details) {
        SubscriptionOverrideAudit a = new SubscriptionOverrideAudit();
        a.setProvider(provider);
        a.setActorUserId(actor != null ? actor.getUserId() : null);
        a.setActorEmail(actor != null && actor.getEmail() != null ? actor.getEmail() : "unknown");
        a.setAction(action);
        a.setReason(reason);
        a.setDetails(details.length() > 1000 ? details.substring(0, 1000) : details);
        a.setCreatedAt(Instant.now(clock));
        auditRepository.save(a);
    }

    private void activateProviderForMarketplace(Provider provider) {
        if (provider.getStatus() == ProviderStatus.PENDING) {
            provider.setStatus(ProviderStatus.ACTIVE);
            providerRepository.save(provider);
        }
    }

    private Provider loadProvider(Long providerId) {
        return providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
    }

    private static String requireReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw badRequest("A reason is required");
        }
        if (r.length() > MAX_REASON_LENGTH) {
            throw badRequest("Reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
        return r;
    }

    private static String label(SubscriptionPlan plan) {
        return plan == SubscriptionPlan.PREMIUM ? "Premium" : "Basic";
    }

    private static String fmt(Instant i) {
        return i == null ? "none" : i.toString();
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", message);
    }
}
