package com.agrimarket.service;

import com.agrimarket.api.dto.ClientOtpVerifyResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.AppProperties;
import com.agrimarket.domain.ClientOtpChallenge;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ClientOtpChallengeRepository;
import com.agrimarket.security.JwtService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientOtpService {

    private static final Logger log = LoggerFactory.getLogger(ClientOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties appProperties;
    private final ClientOtpChallengeRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Always returns 204 to avoid target enumeration. */
    @Transactional
    public void requestOtp(String targetRaw) {
        String target = normalizeTarget(targetRaw);
        if (target.isEmpty()) return;

        // One active challenge per target
        repo.deleteByTarget(target);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        ClientOtpChallenge c = new ClientOtpChallenge();
        c.setTarget(target);
        c.setCodeHash(passwordEncoder.encode(code));
        c.setExpiresAt(Instant.now().plus(appProperties.otp().ttlMinutes(), ChronoUnit.MINUTES));
        c.setAttempts(0);
        repo.save(c);

        // local/dev delivery mechanism: server log
        log.info("Client OTP for {}: {}", target, code);
    }

    @Transactional
    public ClientOtpVerifyResponse verify(String targetRaw, String codeRaw) {
        String target = normalizeTarget(targetRaw);
        String code = codeRaw == null ? "" : codeRaw.trim();
        if (target.isEmpty() || code.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP", "Invalid OTP request");
        }

        ClientOtpChallenge c = repo.findTopByTargetOrderByCreatedAtDesc(target)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP", "Invalid or expired code"));

        if (c.getConsumedAt() != null || c.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP", "Invalid or expired code");
        }
        if (c.getAttempts() >= appProperties.otp().maxAttempts()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED", "Too many attempts. Request a new code.");
        }
        c.setAttempts(c.getAttempts() + 1);

        if (!passwordEncoder.matches(code, c.getCodeHash())) {
            repo.save(c);
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

    private String normalizeTarget(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase();
    }
}

