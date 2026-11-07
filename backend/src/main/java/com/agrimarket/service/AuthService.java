package com.agrimarket.service;

import com.agrimarket.api.dto.ChangePasswordRequest;
import com.agrimarket.api.dto.LoginRequest;
import com.agrimarket.api.dto.LoginResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.api.error.TooManyRequestsException;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.security.LoginAttemptService;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.security.PasswordPolicy;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * One message for unknown email, wrong password and disabled account, so login cannot be used
     * to discover which emails are registered. (Spring's DaoAuthenticationProvider already spends the
     * same BCrypt work on unknown emails.)
     */
    public static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    /**
     * Not transactional on purpose: failure counters written by {@link LoginAttemptService} must be
     * committed even though this method then throws.
     */
    public LoginResponse login(LoginRequest req) {
        String email = req.email() == null ? "" : req.email().trim();

        Optional<Duration> locked = loginAttemptService.lockRemaining(email);
        if (locked.isPresent()) {
            throw lockedException(locked.get());
        }

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        } catch (AuthenticationException e) {
            // Wrong password, unknown email, disabled account: one response, and it counts toward lockout.
            Optional<Duration> lockedNow = loginAttemptService.recordFailure(email);
            if (lockedNow.isPresent()) {
                throw lockedException(lockedNow.get());
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", INVALID_CREDENTIALS);
        }

        MarketUserPrincipal p = (MarketUserPrincipal) auth.getPrincipal();
        loginAttemptService.recordSuccess(p.getEmail());
        UserAccount u = userAccountRepository
                .findByEmailForAuth(p.getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", INVALID_CREDENTIALS));
        String token = jwtService.createToken(u.getId(), u.getEmail(), u.getRole(), p.getProviderId());
        return new LoginResponse(
                token, u.getRole(), p.getProviderId(), u.getEmail(), u.getDisplayName());
    }

    private static TooManyRequestsException lockedException(Duration remaining) {
        return new TooManyRequestsException(
                "ACCOUNT_LOCKED", LoginAttemptService.lockedMessage(remaining), remaining.getSeconds());
    }

    @Transactional
    public void changePassword(MarketUserPrincipal principal, ChangePasswordRequest req) {
        UserAccount u = userAccountRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "User not found"));
        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Current password is incorrect");
        }
        if (passwordEncoder.matches(req.newPassword(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_PASSWORD", "Choose a different password than your current one");
        }
        PasswordPolicy.require(req.newPassword(), u.getEmail());
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        LoginAttemptService.clearLock(u);
        userAccountRepository.save(u);
    }
}
