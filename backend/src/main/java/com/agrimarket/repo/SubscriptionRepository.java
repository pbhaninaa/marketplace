package com.agrimarket.repo;

import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query(
            "SELECT s FROM Subscription s WHERE s.provider.id = :providerId AND s.status = :status AND s.expiresAt > :now ORDER BY s.expiresAt DESC")
    List<Subscription> findActiveForProviderOrderByExpiresAtDesc(
            @Param("providerId") Long providerId, @Param("status") SubscriptionStatus status, @Param("now") Instant now);

    Optional<Subscription> findTopByProviderIdOrderByCreatedAtDesc(Long providerId);
}
