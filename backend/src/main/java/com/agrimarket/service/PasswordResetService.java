package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.PasswordResetProperties;
import com.agrimarket.domain.PasswordResetToken;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.repo.PasswordResetTokenRepository;
import com.agrimarket.repo.UserAccountRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
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

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties props;
    private final Environment environment;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Always succeeds from caller perspective (no email enumeration). */
    @Transactional
    public void requestReset(String email) {
        String trimmed = email == null ? "" : email.trim();
        if (trimmed.isEmpty()) {
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
        tokenRepository.save(new PasswordResetToken(raw, user, expires));

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

        // In local/dev we still log the link for convenience.
        if (isLocalProfile()) {
            log.info("Password reset link for {}: {}", user.getEmail(), link);
        }
        emailService.send(user.getEmail(), subject, plain, html);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_INVALID", "Invalid or expired reset link");
        }
        PasswordResetToken row =
                tokenRepository.findByToken(token.trim()).orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "RESET_INVALID", "Invalid or expired reset link"));
        if (row.getUsedAt() != null || row.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_INVALID", "Invalid or expired reset link");
        }
        UserAccount user = row.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);
        row.setUsedAt(Instant.now());
        tokenRepository.save(row);
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
