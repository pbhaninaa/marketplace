package com.agrimarket.repo;

import com.agrimarket.domain.SubscriptionOverrideAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionOverrideAuditRepository extends JpaRepository<SubscriptionOverrideAudit, Long> {

    List<SubscriptionOverrideAudit> findByProvider_IdOrderByCreatedAtDescIdDesc(Long providerId);
}
