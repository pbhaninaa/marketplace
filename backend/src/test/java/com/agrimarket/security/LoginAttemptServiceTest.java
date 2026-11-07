package com.agrimarket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-07T10:00:00Z"));
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final LoginAttemptService service = new LoginAttemptService(users, 5, Duration.ofMinutes(15), clock);
    private UserAccount alice;

    @BeforeEach
    void setUp() {
        alice = new UserAccount("alice@farm.test", "hash", UserRole.PROVIDER_OWNER, null);
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("alice@farm.test")).thenReturn(Optional.of(alice));
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void locksARealAccountAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            assertThat(service.recordFailure("alice@farm.test")).isEmpty();
        }
        assertThat(alice.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(service.recordFailure("alice@farm.test")).contains(Duration.ofMinutes(15));
        assertThat(alice.getLockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        assertThat(service.lockRemaining("alice@farm.test")).contains(Duration.ofMinutes(15));
    }

    @Test
    void lockExpiresOnItsOwn() {
        for (int i = 0; i < 5; i++) service.recordFailure("alice@farm.test");
        clock.advance(Duration.ofMinutes(15));
        assertThat(service.lockRemaining("alice@farm.test")).isEmpty();
    }

    @Test
    void successClearsTheCounter() {
        service.recordFailure("alice@farm.test");
        service.recordFailure("alice@farm.test");
        service.recordSuccess("alice@farm.test");
        assertThat(alice.getFailedLoginAttempts()).isZero();
        assertThat(alice.getLockedUntil()).isNull();
    }

    @Test
    void successWithNothingToClearDoesNotWrite() {
        service.recordSuccess("alice@farm.test");
        verify(users, never()).save(any(UserAccount.class));
    }

    @Test
    void unknownEmailsLockTheSameWaySoTheyCannotBeToldApart() {
        for (int i = 0; i < 4; i++) {
            assertThat(service.recordFailure("ghost@example.com")).isEmpty();
        }
        assertThat(service.recordFailure("GHOST@example.com ")).contains(Duration.ofMinutes(15));
        assertThat(service.lockRemaining("ghost@example.com")).contains(Duration.ofMinutes(15));
        verify(users, never()).save(any(UserAccount.class));
    }

    @Test
    void blankIdentifierIsIgnored() {
        assertThat(service.recordFailure(" ")).isEmpty();
        assertThat(service.lockRemaining(null)).isEmpty();
    }

    @Test
    void clearLockResetsEntityFields() {
        alice.setFailedLoginAttempts(3);
        alice.setLockedUntil(clock.instant());
        assertThat(LoginAttemptService.clearLock(alice)).isTrue();
        assertThat(alice.getFailedLoginAttempts()).isZero();
        assertThat(alice.getLockedUntil()).isNull();
        assertThat(LoginAttemptService.clearLock(alice)).isFalse();
    }

    @Test
    void lockedMessageRoundsUpToMinutes() {
        assertThat(LoginAttemptService.lockedMessage(Duration.ofSeconds(30))).contains("1 minute ");
        assertThat(LoginAttemptService.lockedMessage(Duration.ofSeconds(61))).contains("2 minutes");
    }
}
