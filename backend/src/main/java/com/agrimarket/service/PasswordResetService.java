package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.AuthSecurityProperties;
import com.agrimarket.config.PasswordResetProperties;
import com.agrimarket.domain.PasswordResetToken;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.repo.PasswordResetTokenRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.LoginAttemptService;
import com.agrimarket.security.PasswordPolicy;
import com.agrimarket.security.RateLimiter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Stored tokens are SHA-256 hex; legacy (pre-hashing) rows hold the raw base64url token. */
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties props;
    private final Environment environment;
    private final EmailService emailService;
    private final RateLimiter rateLimiter;
    private final AuthSecurityProperties securityProperties;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Always succeeds from caller perspective (no email enumeration). */
    @Transactional
    public void requestReset(String email) {
        String trimmed = email == null ? "" : email.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        // Per-address cap so one inbox can't be flooded with reset emails (the per-IP cap is in
        // AuthRateLimitFilter). Over the cap we skip sending but answer exactly as usual.
        String emailKey = "forgot:email:" + trimmed.toLowerCase(Locale.ROOT);
        if (!rateLimiter
                .tryAcquire(emailKey, securityProperties.getPerEmail().getForgotPasswordPerHour(), Duration.ofHours(1))
                .allowed()) {
            log.warn("Password reset email cap reached; not sending another reset email");
            return;
        }
        Optional<UserAccount> userOpt = userAccountRepository.findByEmailIgnoreCase(trimmed);
        if (userOpt.isEmpty()) {
            return;
        }
        UserAccount user = userOpt.get();
        tokenRepository.deleteByUser_Id(user.getId());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int hours = Math.max(1, props.getTokenTtlHours());
        Instant expires = Instant.now().plus(hours, ChronoUnit.HOURS);
        // Only a hash is stored: someone who reads the database still cannot reset accounts.
        tokenRepository.save(new PasswordResetToken(hashToken(raw), user, expires));

        String base = props.getPublicAppBaseUrl().replaceAll("/$", "");
        String encoded = URLEncoder.encode(raw, StandardCharsets.UTF_8);
        String link = base + "/reset-password?token=" + encoded;
        String subject = "Reset your password";
        String plain = """
                Reset your password

                We received a request to reset your password.
                Use the link below to set a new password:

                %s

                If you didn’t request this, you can ignore this email.
                """.formatted(link);

        String html = EmailTemplates.layout(
                "Reset your password",
                "Password reset",
                "We received a request to reset your password. Use the link below to set a new password.",
                java.util.List.of("Reset link: " + link),
                "If you didn’t request this, you can ignore this email."
        );

        // Local/test only: log the link for convenience. Never on deployed profiles, where anyone
        // with log access could use it to take over the account.
        if (isLocalProfile()) {
            log.info("Password reset link for {}: {}", user.getEmail(), link);
        }
        emailService.send(EmailPurpose.SECURITY, user.getEmail(), subject, plain, html);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw invalidLink();
        }
        PasswordResetToken row = findToken(token.trim()).orElseThrow(PasswordResetService::invalidLink);
        if (row.getUsedAt() != null || row.getExpiresAt().isBefore(Instant.now())) {
            throw invalidLink();
        }
        UserAccount user = row.getUser();
        PasswordPolicy.require(newPassword, user.getEmail());
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // A fresh password ends any lockout from earlier failed logins.
        LoginAttemptService.clearLock(user);
        userAccountRepository.save(user);
        row.setUsedAt(Instant.now());
        tokenRepository.save(row);
    }

    /**
     * Look up by hash. Tokens issued before hashing are still accepted until they expire, but only
     * rows that are not themselves a hash, so a hash read from the database never works as a token.
     */
    private Optional<PasswordResetToken> findToken(String rawToken) {
        Optional<PasswordResetToken> hashed = tokenRepository.findByToken(hashToken(rawToken));
        if (hashed.isPresent()) {
            return hashed;
        }
        return tokenRepository.findByToken(rawToken).filter(t -> !SHA256_HEX.matcher(t.getToken()).matches());
    }

    /** SHA-256 hex of the token that goes in the email link (64 chars, fits the token column). */
    public static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static ApiException invalidLink() {
        return new ApiException(HttpStatus.BAD_REQUEST, "RESET_INVALID", "Invalid or expired reset link");
    }

    private boolean isLocalProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }
}
