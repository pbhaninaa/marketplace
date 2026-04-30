package com.agrimarket.repo;

import com.agrimarket.domain.SubscriptionPaymentProof;
import com.agrimarket.domain.SubscriptionProofStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentProofRepository extends JpaRepository<SubscriptionPaymentProof, Long> {
    Optional<SubscriptionPaymentProof> findTopByProviderIdOrderByCreatedAtDesc(Long providerId);

    List<SubscriptionPaymentProof> findByStatusOrderByCreatedAtAsc(SubscriptionProofStatus status);

    @EntityGraph(attributePaths = {"provider", "subscription"})
    List<SubscriptionPaymentProof> findByStatusAndManualVerificationRequiredTrueOrderByCreatedAtAsc(
            SubscriptionProofStatus status);
}

