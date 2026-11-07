package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The platform owner is the first admin ever created, while that account stays enabled. */
class PlatformOwnerTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PlatformOwnerService service = new PlatformOwnerService(users);

    private static UserAccount admin(long id, String email, Instant created) {
        UserAccount u = new UserAccount(email, "x", UserRole.PLATFORM_ADMIN, null);
        u.setId(id);
        u.setCreatedAt(created);
        return u;
    }

    private final UserAccount first = admin(1L, "founder@test", Instant.parse("2026-01-01T09:00:00Z"));
    private final UserAccount later = admin(2L, "ops@test", Instant.parse("2026-05-01T09:00:00Z"));

    private static MarketUserPrincipal principal(UserAccount u) {
        return new MarketUserPrincipal(u);
    }

    @BeforeEach
    void setUp() {
        when(users.findFirstByRoleOrderByCreatedAtAscIdAsc(UserRole.PLATFORM_ADMIN)).thenReturn(Optional.of(first));
    }

    @Test
    void onlyTheFirstAdminIsOwner() {
        assertThat(service.isPlatformOwner(principal(first))).isTrue();
        assertThat(service.isPlatformOwner(principal(later))).isFalse();
        assertThat(service.isPlatformOwner(99L)).isFalse();
        assertThat(service.isPlatformOwner((MarketUserPrincipal) null)).isFalse();
        assertThat(service.isPlatformOwner((Long) null)).isFalse();
    }

    @Test
    void nonAdminRoleWithOwnersIdIsNotOwner() {
        MarketUserPrincipal impersonating =
                new MarketUserPrincipal(1L, "founder@test", "x", UserRole.PROVIDER_OWNER, 5L, true);
        assertThat(service.isPlatformOwner(impersonating)).isFalse();
    }

    @Test
    void disabledFirstAdminLosesOwnerPowersAndNobodyInherits() {
        first.setEnabled(false);
        assertThat(service.isPlatformOwner(principal(first))).isFalse();
        assertThat(service.isPlatformOwner(principal(later))).isFalse();
        assertThat(service.findOwner()).isEmpty();
    }

    @Test
    void noAdminsMeansNoOwner() {
        when(users.findFirstByRoleOrderByCreatedAtAscIdAsc(UserRole.PLATFORM_ADMIN)).thenReturn(Optional.empty());
        assertThat(service.isPlatformOwner(principal(first))).isFalse();
    }

    @Test
    void requireOwnerRejectsOtherAdminsWith403() {
        service.requireOwner(principal(first));
        assertThatThrownBy(() -> service.requireOwner(principal(later)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> service.requireOwner(null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
