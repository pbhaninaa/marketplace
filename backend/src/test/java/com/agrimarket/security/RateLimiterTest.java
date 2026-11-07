package com.agrimarket.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    // Start exactly on a minute boundary so window maths is easy to follow.
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-07T10:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(clock, true);

    @Test
    void allowsUpToTheLimitThenRejectsWithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        RateLimiter.Decision d = limiter.tryAcquire("k", 3, Duration.ofMinutes(1));
        assertThat(d.allowed()).isFalse();
        assertThat(d.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("a", 3, Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("a", 3, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(limiter.tryAcquire("b", 3, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void slidingWindowStopsADoubleBurstAtTheBoundary() {
        clock.advance(Duration.ofSeconds(59));
        for (int i = 0; i < 3; i++) limiter.tryAcquire("k", 3, Duration.ofMinutes(1));
        clock.advance(Duration.ofSeconds(2)); // 1s into the next window: previous window still weighs ~98%
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1)).allowed()).isFalse();
    }

    @Test
    void recoversOnceTheWindowHasPassed() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("k", 3, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(2));
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void rejectedHitsAreNotCounted() {
        for (int i = 0; i < 10; i++) limiter.tryAcquire("k", 3, Duration.ofMinutes(1));
        clock.advance(Duration.ofSeconds(90)); // half the previous window (3 hits) still counts → 1.5
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void resetForgetsAKey() {
        for (int i = 0; i < 3; i++) limiter.tryAcquire("k", 3, Duration.ofMinutes(1));
        limiter.reset("k");
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void disabledLimiterAlwaysAllows() {
        RateLimiter off = new RateLimiter(clock, false);
        for (int i = 0; i < 100; i++) {
            assertThat(off.tryAcquire("k", 1, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        assertThat(off.isEnabled()).isFalse();
    }
}
