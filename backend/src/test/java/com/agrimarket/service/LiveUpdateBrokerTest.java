package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.domain.UserRole;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.live.LiveEvent;
import com.agrimarket.service.live.LiveUpdateBroker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LiveUpdateBrokerTest {

    private final LiveUpdateBroker broker = new LiveUpdateBroker();

    @AfterEach
    void tearDown() {
        broker.shutdown();
    }

    private static MarketUserPrincipal user(long id, UserRole role, Long providerId) {
        return new MarketUserPrincipal(id, "u" + id + "@test.com", "x", role, providerId, true);
    }

    private static final class Capture implements LiveUpdateBroker.Sink {
        final List<LiveEvent> events = new ArrayList<>();
        boolean alive = true;

        @Override
        public boolean send(LiveEvent event) {
            events.add(event);
            return alive;
        }
    }

    @Test
    void providerOnlyReceivesItsOwnOrganisationsEvents() {
        Capture providerA = new Capture();
        Capture providerAStaff = new Capture();
        Capture providerB = new Capture();
        Capture admin = new Capture();
        Capture support = new Capture();
        Capture client = new Capture();
        broker.register(user(1, UserRole.PROVIDER_OWNER, 100L), providerA);
        broker.register(user(2, UserRole.PROVIDER_STAFF, 100L), providerAStaff);
        broker.register(user(3, UserRole.PROVIDER_OWNER, 200L), providerB);
        broker.register(user(4, UserRole.PLATFORM_ADMIN, null), admin);
        broker.register(user(5, UserRole.SUPPORT, null), support);
        broker.register(user(6, UserRole.CLIENT, null), client);

        broker.publish(LiveEvent.of(LiveEvent.ORDER_CREATED, 100L));
        broker.publish(LiveEvent.of(LiveEvent.PAYMENT, 200L));
        broker.publish(LiveEvent.of(LiveEvent.SUBSCRIPTION, null));

        assertThat(providerA.events).extracting(LiveEvent::providerId).containsExactly(100L);
        assertThat(providerAStaff.events).extracting(LiveEvent::providerId).containsExactly(100L);
        assertThat(providerB.events).extracting(LiveEvent::providerId).containsExactly(200L);
        assertThat(admin.events).hasSize(3);
        assertThat(support.events).hasSize(3);
        assertThat(client.events).isEmpty();
    }

    @Test
    void scopingRules() {
        LiveEvent forA = LiveEvent.of(LiveEvent.ORDER_STATUS, 100L);
        assertThat(LiveUpdateBroker.canReceive(UserRole.PROVIDER_VIEWER, 100L, forA)).isTrue();
        assertThat(LiveUpdateBroker.canReceive(UserRole.PROVIDER_ADMIN, 200L, forA)).isFalse();
        assertThat(LiveUpdateBroker.canReceive(UserRole.PROVIDER_OWNER, null, forA)).isFalse();
        assertThat(LiveUpdateBroker.canReceive(UserRole.PROVIDER_OWNER, 100L, LiveEvent.of("X", null))).isFalse();
        assertThat(LiveUpdateBroker.canReceive(UserRole.CLIENT, 100L, forA)).isFalse();
        assertThat(LiveUpdateBroker.canReceive(UserRole.PLATFORM_ADMIN, null, forA)).isTrue();
    }

    @Test
    void deadConnectionsAreDropped() {
        Capture dead = new Capture();
        dead.alive = false;
        broker.register(user(1, UserRole.PLATFORM_ADMIN, null), dead);
        assertThat(broker.subscriberCount()).isEqualTo(1);
        broker.publish(LiveEvent.of(LiveEvent.ORDER_CREATED, 1L));
        assertThat(broker.subscriberCount()).isZero();
    }
}
