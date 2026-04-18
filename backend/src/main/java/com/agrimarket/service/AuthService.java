package com.agrimarket.service;

import com.agrimarket.api.dto.ChangePasswordRequest;
import com.agrimarket.api.dto.LoginRequest;
import com.agrimarket.api.dto.LoginResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.security.MarketUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        Authentication auth =
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        MarketUserPrincipal p = (MarketUserPrincipal) auth.getPrincipal();
        UserAccount u = userAccountRepository
                .findByEmailForAuth(p.getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Invalid credentials"));
        String token = jwtService.createToken(u.getId(), u.getEmail(), u.getRole(), p.getProviderId());
        return new LoginResponse(
                token, u.getRole(), p.getProviderId(), u.getEmail(), u.getDisplayName());
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
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userAccountRepository.save(u);
    }
}
