package com.agrimarket.service.live;

import com.agrimarket.domain.UserRole;
import com.agrimarket.security.MarketUserPrincipal;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory fan-out of {@link LiveEvent}s to Server-Sent Event streams.
 *
 * <ul>
 *   <li>Events are published as Spring application events and delivered only after the surrounding
 *       transaction commits, so a dashboard that reloads on the push sees the new data.</li>
 *   <li>Scoping: admins/support get everything; provider users only their own provider's events;
 *       anyone else gets nothing.</li>
 *   <li>A heartbeat comment every 20s keeps idle connections open through Railway / reverse proxies.</li>
 * </ul>
 *
 * <p>Single-instance only (no cross-node bus). With several backend replicas each node pushes only the
 * events it handled; the browser's 60s polling fallback covers the rest.
 */
@Component
public class LiveUpdateBroker {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdateBroker.class);

    /** Below Railway's/most proxies' request limits; the browser reconnects automatically. */
    static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000L;

    static final long HEARTBEAT_SECONDS = 20;

    /** Delivery target; lets tests capture events without real HTTP streams. */
    public interface Sink {
        /** @return false when the connection is dead and the subscriber should be dropped */
        boolean send(LiveEvent event);

        default boolean heartbeat() {
            return true;
        }
    }

    record Subscriber(UserRole role, Long providerId, Sink sink) {}

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "live-updates");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "live-updates-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public LiveUpdateBroker() {
        heartbeats.scheduleAtFixedRate(this::heartbeatAll, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** Whether a user with this role/provider may see the event. */
    public static boolean canReceive(UserRole role, Long subscriberProviderId, LiveEvent event) {
        if (role == null || event == null) {
            return false;
        }
        return switch (role) {
            case PLATFORM_ADMIN, SUPPORT -> true;
            case PROVIDER_OWNER, PROVIDER_ADMIN, PROVIDER_STAFF, PROVIDER_VIEWER -> subscriberProviderId != null
                    && subscriberProviderId.equals(event.providerId());
            default -> false;
        };
    }

    /** Opens an SSE stream for the signed-in user. */
    public SseEmitter subscribe(MarketUserPrincipal principal) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        String id = register(principal, new EmitterSink(emitter));
        Runnable remove = () -> unregister(id);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(e -> remove.run());
        try {
            // Initial event flushes headers through proxies and tells the client it is connected.
            emitter.send(SseEmitter.event().name("ready").data("{\"type\":\"READY\"}", MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    /** Registers a delivery target; returns its id. */
    public String register(MarketUserPrincipal principal, Sink sink) {
        String id = UUID.randomUUID().toString();
        subscribers.put(id, new Subscriber(principal.getRole(), principal.getProviderId(), sink));
        return id;
    }

    public void unregister(String id) {
        if (id != null) {
            subscribers.remove(id);
        }
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    /** Delivered after commit (or immediately when no transaction is active). */
    @TransactionalEventListener(fallbackExecution = true)
    public void onLiveEvent(LiveEvent event) {
        if (subscribers.isEmpty()) {
            return;
        }
        try {
            dispatcher.execute(() -> publish(event));
        } catch (Exception e) {
            log.debug("Live update dispatch rejected: {}", e.getMessage());
        }
    }

    /** Synchronously delivers an event to every subscriber allowed to see it. */
    public void publish(LiveEvent event) {
        subscribers.forEach((id, s) -> {
            if (!canReceive(s.role(), s.providerId(), event)) {
                return;
            }
            boolean ok;
            try {
                ok = s.sink().send(event);
            } catch (Exception e) {
                ok = false;
            }
            if (!ok) {
                subscribers.remove(id);
            }
        });
    }

    private void heartbeatAll() {
        subscribers.forEach((id, s) -> {
            boolean ok;
            try {
                ok = s.sink().heartbeat();
            } catch (Exception e) {
                ok = false;
            }
            if (!ok) {
                subscribers.remove(id);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        heartbeats.shutdownNow();
        dispatcher.shutdownNow();
        subscribers.values().forEach(s -> {
            if (s.sink() instanceof EmitterSink es) {
                es.complete();
            }
        });
        subscribers.clear();
    }

    private static final class EmitterSink implements Sink {
        private final SseEmitter emitter;

        EmitterSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public synchronized boolean send(LiveEvent event) {
            try {
                emitter.send(SseEmitter.event()
                        .name("update")
                        .data(
                                "{\"type\":\"" + event.type() + "\",\"providerId\":"
                                        + (event.providerId() == null ? "null" : event.providerId()) + "}",
                                MediaType.APPLICATION_JSON));
                return true;
            } catch (Exception e) {
                complete();
                return false;
            }
        }

        @Override
        public synchronized boolean heartbeat() {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
                return true;
            } catch (Exception e) {
                complete();
                return false;
            }
        }

        void complete() {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // already closed
            }
        }
    }
}
