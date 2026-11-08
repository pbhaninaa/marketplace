package com.agrimarket.service;

import com.agrimarket.api.dto.ClientOtpVerifyResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.AppProperties;
import com.agrimarket.config.AuthSecurityProperties;
import com.agrimarket.domain.ClientOtpChallenge;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ClientOtpChallengeRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.security.RateLimiter;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time email codes: guest client sign-in ({@link #PURPOSE_CLIENT_LOGIN}) and proving ownership
 * of the owner email at provider sign-up ({@link #PURPOSE_PROVIDER_SIGNUP}).
 *
 * <p>Codes are 6 digits from {@link SecureRandom}, stored as a BCrypt hash, expire after
 * {@code app.otp.ttl-minutes} and stop working after {@code app.otp.max-attempts} wrong guesses.
 * Each email + purpose can be sent at most {@code app.security.per-email.otp-sends-per15-min} codes
 * per 15 minutes, so requesting new codes cannot be used to flood an inbox or reset the guess count
 * indefinitely.
 */
@Service
@RequiredArgsConstructor
public class ClientOtpService {

    public static final String PURPOSE_CLIENT_LOGIN = "CLIENT_LOGIN";
    public static final String PURPOSE_PROVIDER_SIGNUP = "PROVIDER_SIGNUP";

    static final Duration SEND_WINDOW = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(ClientOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties appProperties;
    private final ClientOtpChallengeRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final Environment environment;
    private final RateLimiter rateLimiter;
    private final AuthSecurityProperties securityProperties;

    private enum Check {
        OK,
        INVALID,
        LOCKED
    }

    /** Guest client sign-in code. Always returns normally to avoid target enumeration. */
    @Transactional
    public void requestOtp(String targetRaw) {
        issue(targetRaw, PURPOSE_CLIENT_LOGIN, true);
    }

    /** Support staff re-sending a client code on the user's behalf (not subject to the per-email cap). */
    @Transactional
    public void resendBySupport(String targetRaw) {
        issue(targetRaw, PURPOSE_CLIENT_LOGIN, false);
    }

    /**
     * Code proving ownership of the owner email at provider sign-up. Always returns normally.
     *
     * @return the code itself only in test environments (SIT/local, with
     *     {@code app.signup.expose-test-code=true}) so the sign-up form can fill it in without a real
     *     email; empty everywhere else, where the code is only ever delivered by email.
     */
    @Transactional
    public Optional<String> requestSignupCode(String emailRaw) {
        String code = issue(emailRaw, PURPOSE_PROVIDER_SIGNUP, true);
        return code != null && exposesTestCodes() ? Optional.of(code) : Optional.empty();
    }

    /** True only on dev/test profiles with the flag on; never on UAT/PROD even if the flag is set. */
    public boolean exposesTestCodes() {
        return isDevDeliveryProfile()
                && Boolean.TRUE.equals(environment.getProperty("app.signup.expose-test-code", Boolean.class, false));
    }

    /** @return the issued code, or null when nothing was issued (bad target or send cap reached). */
    private String issue(String targetRaw, String purpose, boolean enforceSendCap) {
        String target = normalizeTarget(targetRaw);
        if (target.isEmpty()) return null;

        if (enforceSendCap
                && !rateLimiter
                        .tryAcquire(
                                "otp-send:" + purpose + ":" + target,
                                securityProperties.getPerEmail().getOtpSendsPer15Min(),
                                SEND_WINDOW)
                        .allowed()) {
            // Caller still sees success (no enumeration, no inbox flooding).
            log.warn("OTP send cap reached for purpose {}; not sending another code", purpose);
            return null;
        }

        // One active challenge per target + purpose
        repo.deleteByTargetAndPurpose(target, purpose);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        ClientOtpChallenge c = new ClientOtpChallenge();
        c.setTarget(target);
        c.setPurpose(purpose);
        c.setCodeHash(passwordEncoder.encode(code));
        c.setExpiresAt(Instant.now().plus(appProperties.otp().ttlMinutes(), ChronoUnit.MINUTES));
        c.setAttempts(0);
        repo.save(c);

        if (isDevDeliveryProfile()) {
            log.info("OTP ({}) for {}: {}", purpose, target, code);
        } else {
            boolean signup = PURPOSE_PROVIDER_SIGNUP.equals(purpose);
            String subject = signup ? "Confirm your email address" : "Your verification code";
            String intro = signup ? "Use this code to confirm your email and finish creating your account: "
                    : "Your verification code is: ";
            String plain = intro + code + "\n\nIt expires in " + appProperties.otp().ttlMinutes() + " minutes."
                    + "\n\nIf you didn't request this, you can ignore this email.";
            emailService.send(
                    EmailPurpose.SECURITY,
                    target,
                    subject,
                    plain,
                    "<p>" + (signup ? "Use this code to confirm your email and finish creating your account:"
                            : "Your verification code is:") + " <strong>" + code + "</strong>.</p>"
                            + "<p>It expires in " + appProperties.otp().ttlMinutes() + " minutes.</p>");
        }
        return code;
    }

    /**
     * Guest client sign-in. Wrong guesses must be committed even though we then throw, otherwise the
     * attempt cap would be rolled back with the transaction and never take effect.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public ClientOtpVerifyResponse verify(String targetRaw, String codeRaw) {
        String target = normalizeTarget(targetRaw);
        String code = codeRaw == null ? "" : codeRaw.trim();
        if (target.isEmpty() || code.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP", "Invalid OTP request");
        }

        ClientOtpChallenge c = repo.findTopByTargetAndPurposeOrderByCreatedAtDesc(target, PURPOSE_CLIENT_LOGIN)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP", "Invalid or expired code"));

        Check result = check(c, code);
        if (result == Check.LOCKED) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED", "Too many attempts. Request a new code.");
        }
        if (result == Check.INVALID) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP", "Invalid or expired code");
        }

        c.setConsumedAt(Instant.now());
        repo.save(c);

        // Guest client session: issue short-lived JWT-like token using existing JWT service.
        // We don't persist a client user account for guest flow.
        long pseudoUserId = Math.abs(target.hashCode()) + 10_000L;
        String token = jwtService.createToken(pseudoUserId, target, UserRole.CLIENT, null);
        return new ClientOtpVerifyResponse(token, UserRole.CLIENT, target);
    }

    /**
     * Check a sign-up code but leave it usable, so the form can fail on another field and be
     * resubmitted. Wrong guesses still count. Call {@link #consumeSignupCode} once the account exists.
     */
    @Transactional
    public boolean verifySignupCode(String emailRaw, String codeRaw) {
        String target = normalizeTarget(emailRaw);
        String code = codeRaw == null ? "" : codeRaw.trim();
        if (target.isEmpty() || code.isEmpty()) return false;
        Optional<ClientOtpChallenge> c =
                repo.findTopByTargetAndPurposeOrderByCreatedAtDesc(target, PURPOSE_PROVIDER_SIGNUP);
        return c.isPresent() && check(c.get(), code) == Check.OK;
    }

    /** Use up the sign-up code for this email. */
    @Transactional
    public void consumeSignupCode(String emailRaw) {
        String target = normalizeTarget(emailRaw);
        if (target.isEmpty()) return;
        repo.findTopByTargetAndPurposeOrderByCreatedAtDesc(target, PURPOSE_PROVIDER_SIGNUP).ifPresent(c -> {
            if (c.getConsumedAt() == null) {
                c.setConsumedAt(Instant.now());
                repo.save(c);
            }
        });
    }

    /** Validates {@code code} against the challenge, counting a wrong guess. */
    private Check check(ClientOtpChallenge c, String code) {
        if (c.getConsumedAt() != null || c.getExpiresAt().isBefore(Instant.now())) {
            return Check.INVALID;
        }
        if (c.getAttempts() >= appProperties.otp().maxAttempts()) {
            return Check.LOCKED;
        }
        if (!passwordEncoder.matches(code, c.getCodeHash())) {
            c.setAttempts(c.getAttempts() + 1);
            repo.save(c);
            return Check.INVALID;
        }
        return Check.OK;
    }

    private String normalizeTarget(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase();
    }

    private boolean isDevDeliveryProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p) || "sit".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }
}
