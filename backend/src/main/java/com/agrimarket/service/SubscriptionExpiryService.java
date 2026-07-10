package com.agrimarket.service;

import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.SubscriptionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks ACTIVE subscriptions as EXPIRED once {@code expiresAt} has passed (monthly = 30 days from approval).
 */
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryService.class);

    private final SubscriptionRepository subscriptionRepository;

    /** Every hour — also runs shortly after startup via fixedDelay. */
    @Scheduled(fixedDelayString = "${app.subscription.expiry-check-ms:3600000}")
    @Transactional
    public void expireLapsedSubscriptions() {
        Instant now = Instant.now();
        List<Subscription> lapsed =
                subscriptionRepository.findLapsedByStatus(SubscriptionStatus.ACTIVE, now);
        if (lapsed.isEmpty()) {
            return;
        }
        for (Subscription s : lapsed) {
            s.setStatus(SubscriptionStatus.EXPIRED);
        }
        subscriptionRepository.saveAll(lapsed);
        log.info("Marked {} subscription(s) EXPIRED (past expiresAt)", lapsed.size());
    }
}
