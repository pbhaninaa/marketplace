package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform owner = the first {@link UserRole#PLATFORM_ADMIN} ever created (earliest {@code created_at},
 * ties broken by id) — i.e. the account made by first-run setup or the default-admin seeder — and only
 * while that account is still enabled. If the first admin is disabled, nobody is owner (ownership is
 * never silently handed to a later admin). Mirrors Wheel Hub's "first admin" rule.
 */
@Service
@RequiredArgsConstructor
public class PlatformOwnerService {

    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public boolean isPlatformOwner(MarketUserPrincipal actor) {
        if (actor == null || actor.getRole() != UserRole.PLATFORM_ADMIN || actor.getUserId() == null) {
            return false;
        }
        return isPlatformOwner(actor.getUserId());
    }

    @Transactional(readOnly = true)
    public boolean isPlatformOwner(Long userId) {
        if (userId == null) {
            return false;
        }
        return findOwner().map(owner -> owner.getId().equals(userId)).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findOwner() {
        return userAccountRepository
                .findFirstByRoleOrderByCreatedAtAscIdAsc(UserRole.PLATFORM_ADMIN)
                .filter(UserAccount::isEnabled);
    }

    /** 401 when anonymous, 403 for anyone but the platform owner. */
    public void requireOwner(MarketUserPrincipal actor) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        if (!isPlatformOwner(actor)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "PLATFORM_OWNER_ONLY", "Only the platform owner (first admin) can do this");
        }
    }
}
