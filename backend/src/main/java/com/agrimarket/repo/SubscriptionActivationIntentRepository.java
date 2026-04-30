package com.agrimarket.repo;

import com.agrimarket.domain.SubscriptionActivationIntent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionActivationIntentRepository extends JpaRepository<SubscriptionActivationIntent, Long> {
    Optional<SubscriptionActivationIntent> findByIdAndProviderId(Long id, Long providerId);
}

