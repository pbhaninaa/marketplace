package com.agrimarket.repo;

import com.agrimarket.domain.SubscriptionActivationIntent;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionActivationIntentRepository extends JpaRepository<SubscriptionActivationIntent, Long> {
    Optional<SubscriptionActivationIntent> findByIdAndProviderId(Long id, Long providerId);

    Optional<SubscriptionActivationIntent> findByGatewayMerchantRef(String gatewayMerchantRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM SubscriptionActivationIntent i WHERE i.gatewayMerchantRef = :gatewayMerchantRef")
    Optional<SubscriptionActivationIntent> findByGatewayMerchantRefForUpdate(
            @Param("gatewayMerchantRef") String gatewayMerchantRef);
}

