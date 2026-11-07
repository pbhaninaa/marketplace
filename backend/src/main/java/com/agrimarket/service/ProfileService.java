package com.agrimarket.service;

import com.agrimarket.api.dto.ProfileResponse;
import com.agrimarket.api.dto.ProfileUpdateRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public ProfileResponse get(MarketUserPrincipal principal) {
        return toResponse(load(principal));
    }

    @Transactional
    public ProfileResponse update(MarketUserPrincipal principal, ProfileUpdateRequest req) {
        UserAccount u = load(principal);
        u.setFirstName(trimToNull(req.firstName()));
        u.setLastName(trimToNull(req.lastName()));
        u.setDisplayName(trimToNull(req.displayName()));
        u.setPhoneNumber(trimToNull(req.phoneNumber()));
        return toResponse(userAccountRepository.save(u));
    }

    private UserAccount load(MarketUserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Sign in to view your profile");
        }
        return userAccountRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found"));
    }

    private static ProfileResponse toResponse(UserAccount u) {
        return new ProfileResponse(
                u.getId(),
                u.getEmail(),
                u.getRole(),
                u.getFirstName(),
                u.getLastName(),
                u.getDisplayName(),
                u.getPhoneNumber(),
                u.getProvider() != null ? u.getProvider().getName() : null,
                u.getCreatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
