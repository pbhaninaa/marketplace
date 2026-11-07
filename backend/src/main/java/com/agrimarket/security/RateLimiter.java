package com.agrimarket.security;

import com.agrimarket.config.AuthSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Small in-memory sliding-window rate limiter (no external dependency).
 *
 * <p>Each key (e.g. {@code "login-1m:ip:1.2.3.4"}) keeps the current and previous fixed window; the
 * count is weighted across both so bursts at a window edge cannot double the limit.
 *
 * <p>Counters are per JVM. With several API instances each instance enforces the limit on its own
 * share of traffic; account lockout (stored in the database) still applies globally.
 */
@Component
public class RateLimiter {

    /** Clean up stale keys once the map grows past this many entries. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public RateLimiter(AuthSecurityProperties props) {
        this(Clock.systemUTC(), props.getRateLimit().isEnabled());
    }

    public RateLimiter(Clock clock, boolean enabled) {
        this.clock = clock;
        this.enabled = enabled;
    }

    /** False when switched off with app.security.rate-limit.enabled=false (integration tests). */
    public boolean isEnabled() {
        return enabled;
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOW = new Decision(true, 0);
    }

    /**
     * Records one hit for {@code key} and says whether it is within {@code limit} per {@code window}.
     * Rejected hits are not counted, so a client that backs off recovers on schedule.
     */
    public Decision tryAcquire(String key, int limit, Duration window) {
        if (!enabled || limit <= 0) return Decision.ALLOW;
        long now = clock.millis();
        long size = Math.max(1, window.toMillis());
        long windowStart = now - (now % size);

        if (windows.size() > CLEANUP_THRESHOLD) cleanup(now);

        Window w = windows.computeIfAbsent(key, k -> new Window(windowStart, size));
        synchronized (w) {
            w.roll(windowStart);
            double prevWeight = 1.0 - (double) (now - windowStart) / size;
            double estimated = w.previous * prevWeight + w.current;
            if (estimated + 1 > limit) {
                long retry = (long) Math.ceil((windowStart + size - now) / 1000.0);
                return new Decision(false, Math.max(1, retry));
            }
            w.current++;
            return Decision.ALLOW;
        }
    }

    /** Forget a key. */
    public void reset(String key) {
        windows.remove(key);
    }

    private void cleanup(long now) {
        windows.entrySet().removeIf(e -> now - e.getValue().start >= 2 * e.getValue().size);
    }

    private static final class Window {
        final long size;
        long start;
        int current;
        int previous;

        Window(long start, long size) {
            this.start = start;
            this.size = size;
        }

        void roll(long windowStart) {
            if (windowStart == start) return;
            previous = (windowStart - start == size) ? current : 0;
            current = 0;
            start = windowStart;
        }
    }
}
