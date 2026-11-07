package com.agrimarket.security;

import com.agrimarket.config.AuthSecurityProperties;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.repo.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Temporary account lockout after repeated failed logins (brute force / password spraying).
 *
 * <p>Real accounts keep their counter in the {@code users} table so the lock holds across restarts
 * and API instances. Emails that don't match an account get the same treatment in memory, so the
 * "locked" response cannot be used to discover which emails are registered.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int UNKNOWN_CLEANUP_THRESHOLD = 10_000;

    private final UserAccountRepository userAccountRepository;
    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;

    /** In-memory counters for identifiers that are not real accounts. */
    private final Map<String, UnknownState> unknown = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(UserAccountRepository userAccountRepository, AuthSecurityProperties props) {
        this(
                userAccountRepository,
                props.getLogin().getMaxFailures(),
                Duration.ofMinutes(Math.max(1, props.getLogin().getLockMinutes())),
                Clock.systemUTC());
    }

    LoginAttemptService(UserAccountRepository userAccountRepository, int maxFailures, Duration lockDuration, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.maxFailures = Math.max(1, maxFailures);
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    /** Time left on the lock for this email, if locked. */
    @Transactional(readOnly = true)
    public Optional<Duration> lockRemaining(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        Instant now = clock.instant();
        Optional<UserAccount> user = userAccountRepository.findByEmailIgnoreCase(email.trim());
        Instant until = user.isPresent()
                ? user.get().getLockedUntil()
                : Optional.ofNullable(unknown.get(key(email))).map(s -> s.lockedUntil).orElse(null);
        if (until == null || !until.isAfter(now)) return Optional.empty();
        return Optional.of(Duration.between(now, until));
    }

    /** Count a failed login. Returns the lock duration when this failure triggered a lock. */
    @Transactional
    public Optional<Duration> recordFailure(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        Instant now = clock.instant();
        Optional<UserAccount> userOpt = userAccountRepository.findByEmailIgnoreCase(email.trim());
        if (userOpt.isPresent()) {
            UserAccount user = userOpt.get();
            int failures = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            if (failures >= maxFailures) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(now.plus(lockDuration));
                userAccountRepository.save(user);
                log.warn("Login locked for user id {} for {} after {} failed attempts", user.getId(), lockDuration, failures);
                return Optional.of(lockDuration);
            }
            user.setFailedLoginAttempts(failures);
            userAccountRepository.save(user);
            return Optional.empty();
        }

        if (unknown.size() > UNKNOWN_CLEANUP_THRESHOLD) cleanupUnknown(now);
        UnknownState state = unknown.computeIfAbsent(key(email), k -> new UnknownState());
        synchronized (state) {
            state.failures++;
            state.lastSeen = now;
            if (state.failures >= maxFailures) {
                state.failures = 0;
                state.lockedUntil = now.plus(lockDuration);
                return Optional.of(lockDuration);
            }
        }
        return Optional.empty();
    }

    /** Successful login: clear the failure counter. */
    @Transactional
    public void recordSuccess(String email) {
        clear(email);
    }

    /** Clear counter and lock (successful login, password reset). */
    @Transactional
    public void clear(String email) {
        if (email == null || email.isBlank()) return;
        userAccountRepository.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            if (clearLock(user)) userAccountRepository.save(user);
        });
        unknown.remove(key(email));
    }

    /**
     * Reset the lockout fields on an entity the caller is about to save (e.g. after a password
     * reset). Returns true when something changed.
     */
    public static boolean clearLock(UserAccount user) {
        boolean dirty = (user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() != 0)
                || user.getLockedUntil() != null;
        if (dirty) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        return dirty;
    }

    public static String lockedMessage(Duration remaining) {
        long minutes = Math.max(1, (remaining.getSeconds() + 59) / 60);
        return "Too many failed sign-in attempts. Try again in " + minutes
                + (minutes == 1 ? " minute" : " minutes") + " or reset your password.";
    }

    private static String key(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private void cleanupUnknown(Instant now) {
        Instant cutoff = now.minus(lockDuration.multipliedBy(2));
        unknown.entrySet().removeIf(e -> e.getValue().lastSeen.isBefore(cutoff)
                && (e.getValue().lockedUntil == null || e.getValue().lockedUntil.isBefore(now)));
    }

    private static final class UnknownState {
        int failures;
        Instant lockedUntil;
        Instant lastSeen = Instant.EPOCH;
    }
}
