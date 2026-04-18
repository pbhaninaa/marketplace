package com.agrimarket.service;

import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.SubscriptionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(Long providerId) {
        return subscriptionRepository
                .findTopActiveForProvider(providerId, SubscriptionStatus.ACTIVE, Instant.now())
                .isPresent();
    }
}
