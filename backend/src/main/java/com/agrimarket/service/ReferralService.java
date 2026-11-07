package com.agrimarket.service;

import com.agrimarket.api.dto.ReferralDtos.AdminReferralRow;
import com.agrimarket.api.dto.ReferralDtos.AdminStats;
import com.agrimarket.api.dto.ReferralDtos.CodeLookupResponse;
import com.agrimarket.api.dto.ReferralDtos.InviteStatus;
import com.agrimarket.api.dto.ReferralDtos.MyReferralRow;
import com.agrimarket.api.dto.ReferralDtos.MyReferralsResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.Referral;
import com.agrimarket.domain.ReferralSettings;
import com.agrimarket.domain.ReferralSource;
import com.agrimarket.domain.ReferralStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ReferralRepository;
import com.agrimarket.repo.ReferralSettingsRepository;
import com.agrimarket.repo.SubscriptionRepository;
import com.agrimarket.repo.UserAccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider-to-provider referral programme.
 *
 * <ul>
 *   <li>Every provider user has a personal referral code (generated on first use).
 *   <li>A new provider names a referrer by entering that code on the sign-up form (pre-filled from an
 *       invite link) or, later, from the provider portal within the admin-configured claim window and
 *       before their first subscription payment.
 *   <li>When the referred provider's first subscription is paid via Peach, the referrer earns the
 *       admin-configured commission percent of that payment. The rate is frozen on the referral.
 *   <li>Payouts are manual: an admin marks earned commission as paid.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    static final Set<UserRole> REFERRER_ROLES = EnumSet.of(
            UserRole.PROVIDER_OWNER, UserRole.PROVIDER_ADMIN, UserRole.PROVIDER_STAFF, UserRole.PROVIDER_VIEWER);
    /** Only people who run the business may say who referred it. */
    static final Set<UserRole> CLAIMER_ROLES = EnumSet.of(UserRole.PROVIDER_OWNER, UserRole.PROVIDER_ADMIN);

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4,32}$");
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReferralRepository referralRepository;
    private final ReferralSettingsRepository settingsRepository;
    private final UserAccountRepository userAccountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    // ===================== settings =====================

    @Transactional
    public ReferralSettings getSettings() {
        return settingsRepository.findById(ReferralSettings.SINGLETON_ID).orElseGet(() -> {
            ReferralSettings s = new ReferralSettings();
            s.setId(ReferralSettings.SINGLETON_ID);
            s.setUpdatedAt(Instant.now(clock));
            return settingsRepository.save(s);
        });
    }

    /** Applies to commission earned from now on; earned referrals keep their frozen rate. */
    @Transactional
    public ReferralSettings updateSettings(BigDecimal commissionPercent, Integer claimWindowDays, String actor) {
        if (commissionPercent == null
                || commissionPercent.compareTo(BigDecimal.ZERO) < 0
                || commissionPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFERRAL_SETTINGS", "Commission must be between 0 and 100 percent");
        }
        if (claimWindowDays == null || claimWindowDays < 1 || claimWindowDays > 365) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFERRAL_SETTINGS", "Claim window must be between 1 and 365 days");
        }
        ReferralSettings s = getSettings();
        s.setCommissionPercent(commissionPercent.setScale(2, RoundingMode.HALF_UP));
        s.setClaimWindowDays(claimWindowDays);
        s.setUpdatedBy(actor);
        s.setUpdatedAt(Instant.now(clock));
        return settingsRepository.save(s);
    }

    private BigDecimal commissionPercent() {
        BigDecimal p = getSettings().getCommissionPercent();
        return p != null ? p : ReferralSettings.DEFAULT_COMMISSION_PERCENT;
    }

    private int claimWindowDays() {
        Integer d = getSettings().getClaimWindowDays();
        return d != null && d > 0 ? d : ReferralSettings.DEFAULT_CLAIM_WINDOW_DAYS;
    }

    // ===================== codes =====================

    public static String normalizeCode(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("\\s+", "").replace("-", "").toUpperCase(Locale.ROOT);
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** The user's personal code, generated on first request. Provider users only. */
    @Transactional
    public String getOrCreateCode(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER", "Account not found"));
        if (!REFERRER_ROLES.contains(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REFERRAL", "Only provider accounts can refer businesses");
        }
        if (user.getReferralCode() == null) {
            user.setReferralCode(generateUniqueCode(user));
            userAccountRepository.save(user);
        }
        return user.getReferralCode();
    }

    private String generateUniqueCode(UserAccount user) {
        String base = user.getProvider() != null ? user.getProvider().getName() : user.getEmail();
        String prefix = base == null ? "" : base.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (prefix.length() > 6) prefix = prefix.substring(0, 6);
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < 5; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!userAccountRepository.existsByReferralCode(code)) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REFERRAL", "Could not generate a referral code");
    }

    /** Resolves a code to an enabled provider user who may refer. */
    @Transactional(readOnly = true)
    public Optional<UserAccount> resolveReferrer(String rawCode) {
        String code = normalizeCode(rawCode);
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            return Optional.empty();
        }
        return userAccountRepository.findByReferralCode(code)
                .filter(UserAccount::isEnabled)
                .filter(u -> REFERRER_ROLES.contains(u.getRole()) && u.getProvider() != null);
    }

    @Transactional(readOnly = true)
    public CodeLookupResponse lookup(String rawCode) {
        return resolveReferrer(rawCode)
                .map(u -> new CodeLookupResponse(true, referrerBusinessName(u)))
                .orElse(new CodeLookupResponse(false, null));
    }

    // ===================== capture =====================

    /**
     * Called from provider registration. Blank code: no-op. Unknown code: rejected so the person can
     * fix a typo (the whole registration rolls back).
     */
    @Transactional
    public void recordAtRegistration(String rawCode, UserAccount newOwner, Provider newProvider) {
        if (normalizeCode(rawCode) == null) {
            return;
        }
        UserAccount referrer = requireRegistrationReferrer(rawCode);
        createReferral(referrer, newOwner, newProvider, normalizeCode(rawCode), ReferralSource.REGISTRATION);
    }

    /** Fail fast (before any account is created) when a non-blank code is unknown. */
    @Transactional(readOnly = true)
    public void validateRegistrationCode(String rawCode) {
        if (normalizeCode(rawCode) != null) {
            requireRegistrationReferrer(rawCode);
        }
    }

    private UserAccount requireRegistrationReferrer(String rawCode) {
        return resolveReferrer(rawCode).orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST, "INVALID_REFERRAL_CODE", "That referral code is not valid. Check it or leave it blank."));
    }

    /**
     * Signed-in provider owner/admin says who referred their business. Idempotent when the same
     * referrer is claimed again.
     */
    @Transactional
    public Referral claim(Long actorUserId, String rawCode) {
        UserAccount actor = userAccountRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER", "Account not found"));
        if (!CLAIMER_ROLES.contains(actor.getRole()) || actor.getProvider() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REFERRAL", "Only the business owner or an admin can add a referral code");
        }
        Provider provider = actor.getProvider();
        UserAccount referrer = resolveReferrer(rawCode).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFERRAL_CODE", "That referral code is not valid"));

        Optional<Referral> existing = referralRepository.findByRefereeProvider_Id(provider.getId());
        if (existing.isPresent()) {
            if (existing.get().getReferrer().getId().equals(referrer.getId())) {
                return existing.get();
            }
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REFERRED", "Your business already has a referrer");
        }
        claimBlockedReason(provider).ifPresent(reason -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFERRAL_CLAIM_CLOSED", reason);
        });
        return createReferral(referrer, actor, provider, normalizeCode(rawCode), ReferralSource.CLAIM);
    }

    private Referral createReferral(
            UserAccount referrer, UserAccount refereeUser, Provider refereeProvider, String code, ReferralSource source) {
        if (refereeProvider == null || refereeProvider.getId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFERRAL", "Referred business is required");
        }
        boolean sameUser = refereeUser != null && referrer.getId().equals(refereeUser.getId());
        boolean sameEmail = refereeUser != null && referrer.getEmail().equalsIgnoreCase(refereeUser.getEmail());
        boolean sameBusiness = referrer.getProvider() != null
                && refereeProvider.getId().equals(referrer.getProvider().getId());
        if (sameUser || sameEmail || sameBusiness) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_REFERRAL", "You can't use a referral code from your own business");
        }
        if (referralRepository.findByRefereeProvider_Id(refereeProvider.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REFERRED", "This business already has a referrer");
        }
        Referral r = new Referral();
        r.setReferrer(referrer);
        r.setReferrerProvider(referrer.getProvider());
        r.setRefereeProvider(refereeProvider);
        r.setRefereeUser(refereeUser);
        r.setReferralCode(code);
        r.setSource(source);
        r.setStatus(ReferralStatus.PENDING);
        r.setCreatedAt(Instant.now(clock));
        return referralRepository.save(r);
    }

    /** Why this business may no longer add a referrer, or empty when it still can. */
    @Transactional
    public Optional<String> claimBlockedReason(Provider provider) {
        int days = claimWindowDays();
        Instant deadline = claimDeadline(provider, days);
        if (deadline != null && Instant.now(clock).isAfter(deadline)) {
            return Optional.of("Referral codes can only be added within " + days + " days of signing up");
        }
        if (subscriptionRepository.countByProvider_Id(provider.getId()) > 0) {
            return Optional.of("Referral codes can't be added after your first subscription payment");
        }
        return Optional.empty();
    }

    private static Instant claimDeadline(Provider provider, int days) {
        return provider.getCreatedAt() != null ? provider.getCreatedAt().plus(days, ChronoUnit.DAYS) : null;
    }

    // ===================== commission (Peach confirmation) =====================

    /**
     * Records the referrer's commission when a referred provider's first subscription is paid via a
     * verified Peach notification. Idempotent: webhook retries (same reference) and later renewals
     * never record a second commission.
     *
     * <p>Runs inside the webhook transaction but never marks it rollback-only: a referral problem
     * must not undo the provider's subscription activation.
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public Optional<Referral> recordQualifyingPayment(
            Long providerId, Long subscriptionId, BigDecimal amountPaid, String paymentRef) {
        if (providerId == null) return Optional.empty();
        Optional<Referral> opt = referralRepository.findByRefereeProvider_Id(providerId);
        if (opt.isEmpty()) return Optional.empty();
        Referral r = opt.get();
        if (r.getStatus() != ReferralStatus.PENDING) {
            return Optional.of(r); // already earned (retry or renewal) — nothing to do
        }
        if (paymentRef != null && paymentRef.equals(r.getQualifyingPaymentRef())) {
            return Optional.of(r);
        }
        // First subscription only: any earlier subscription means this payment is not the first.
        long earlier = subscriptionId != null
                ? subscriptionRepository.countByProvider_IdAndIdNot(providerId, subscriptionId)
                : subscriptionRepository.countByProvider_Id(providerId);
        if (earlier > 0) {
            log.info("Referral {}: provider {} already had a subscription; no commission", r.getId(), providerId);
            return Optional.of(r);
        }
        if (amountPaid == null || amountPaid.signum() <= 0) {
            return Optional.of(r);
        }
        BigDecimal percent = commissionPercent();
        BigDecimal commission = amountPaid.multiply(percent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        r.setStatus(ReferralStatus.EARNED);
        r.setPaymentAmount(amountPaid.setScale(2, RoundingMode.HALF_UP));
        r.setCommissionPercent(percent);
        r.setCommissionAmount(commission);
        r.setQualifyingPaymentRef(paymentRef);
        r.setQualifyingSubscriptionId(subscriptionId);
        r.setEarnedAt(Instant.now(clock));
        return Optional.of(referralRepository.save(r));
    }

    // ===================== payouts (admin) =====================

    @Transactional
    public Referral markPaid(Long referralId, String note, String actor) {
        Referral r = referralRepository.findById(referralId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REFERRAL", "Referral not found"));
        if (r.getStatus() == ReferralStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "REFERRAL", "Commission already marked as paid");
        }
        if (r.getStatus() != ReferralStatus.EARNED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFERRAL", "No commission has been earned yet");
        }
        r.setStatus(ReferralStatus.PAID);
        r.setPaidOutAt(Instant.now(clock));
        r.setPaidOutBy(actor);
        String trimmed = note == null ? null : note.trim();
        r.setPayoutNote(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        return referralRepository.save(r);
    }

    // ===================== read models =====================

    @Transactional
    public MyReferralsResponse myReferrals(Long userId) {
        String code = getOrCreateCode(userId);
        UserAccount me = userAccountRepository.findById(userId).orElseThrow();
        List<MyReferralRow> rows = referralRepository.findByReferrer_IdOrderByCreatedAtDesc(userId).stream()
                .map(r -> new MyReferralRow(
                        r.getId(),
                        r.getRefereeProvider().getName(),
                        r.getCreatedAt(),
                        r.getStatus(),
                        r.getCommissionPercent(),
                        r.getCommissionAmount(),
                        r.getEarnedAt(),
                        r.getPaidOutAt()))
                .toList();
        BigDecimal awaiting = money(referralRepository.sumCommissionForReferrer(userId, ReferralStatus.EARNED));
        BigDecimal paid = money(referralRepository.sumCommissionForReferrer(userId, ReferralStatus.PAID));
        return new MyReferralsResponse(
                code,
                commissionPercent(),
                claimWindowDays(),
                rows.size(),
                referralRepository.countByReferrer_IdAndStatusIn(
                        userId, List.of(ReferralStatus.EARNED, ReferralStatus.PAID)),
                awaiting.add(paid),
                awaiting,
                paid,
                inviteStatus(me),
                rows);
    }

    private InviteStatus inviteStatus(UserAccount me) {
        Provider provider = me.getProvider();
        if (provider == null) {
            return new InviteStatus(null, false, null, null);
        }
        Optional<Referral> mine = referralRepository.findByRefereeProvider_Id(provider.getId());
        if (mine.isPresent()) {
            return new InviteStatus(referrerBusinessName(mine.get().getReferrer()), false, null, null);
        }
        if (!CLAIMER_ROLES.contains(me.getRole())) {
            return new InviteStatus(null, false, null, "Only the business owner or an admin can add a referral code");
        }
        Optional<String> blocked = claimBlockedReason(provider);
        return new InviteStatus(
                null,
                blocked.isEmpty(),
                blocked.isEmpty() ? claimDeadline(provider, claimWindowDays()) : null,
                blocked.orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<AdminReferralRow> adminSearch(
            ReferralStatus status, Instant from, Instant to, String referrer, String referee, Pageable pageable) {
        return referralRepository
                .search(status, from, to, likePattern(referrer), likePattern(referee), pageable)
                .map(ReferralService::toAdminRow);
    }

    @Transactional(readOnly = true)
    public AdminStats adminStats() {
        long pending = referralRepository.countByStatus(ReferralStatus.PENDING);
        long earned = referralRepository.countByStatus(ReferralStatus.EARNED);
        long paid = referralRepository.countByStatus(ReferralStatus.PAID);
        return new AdminStats(
                pending + earned + paid,
                pending,
                earned,
                paid,
                money(referralRepository.sumCommission(ReferralStatus.EARNED)),
                money(referralRepository.sumCommission(ReferralStatus.PAID)));
    }

    public static AdminReferralRow toAdminRow(Referral r) {
        UserAccount ref = r.getReferrer();
        return new AdminReferralRow(
                r.getId(),
                ref.getId(),
                ref.getEmail(),
                ref.getDisplayName(),
                r.getReferrerProvider() != null ? r.getReferrerProvider().getName() : null,
                r.getReferralCode(),
                r.getRefereeProvider().getId(),
                r.getRefereeProvider().getName(),
                r.getRefereeUser() != null ? r.getRefereeUser().getEmail() : null,
                r.getSource(),
                r.getStatus(),
                r.getPaymentAmount(),
                r.getCommissionPercent(),
                r.getCommissionAmount(),
                r.getQualifyingPaymentRef(),
                r.getCreatedAt(),
                r.getEarnedAt(),
                r.getPaidOutAt(),
                r.getPaidOutBy(),
                r.getPayoutNote());
    }

    private static String likePattern(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        String escaped = t.replace("%", "").replace("_", "");
        return escaped.isEmpty() ? null : "%" + escaped + "%";
    }

    private static String referrerBusinessName(UserAccount u) {
        if (u.getProvider() != null && u.getProvider().getName() != null) {
            return u.getProvider().getName();
        }
        return u.getDisplayName();
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }
}
