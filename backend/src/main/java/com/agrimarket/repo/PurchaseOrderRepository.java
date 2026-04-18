package com.agrimarket.repo;

import com.agrimarket.domain.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Page<PurchaseOrder> findByProvider_IdOrderByCreatedAtDesc(Long providerId, Pageable pageable);

    Optional<PurchaseOrder> findByVerificationCode(String verificationCode);

    @Query(
            """
            SELECT COUNT(o) FROM PurchaseOrder o
            WHERE o.provider.id = :providerId
            AND o.createdAt >= :from
            AND o.createdAt < :to
            """)
    long countForProviderBetween(
            @Param("providerId") Long providerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM PurchaseOrder o
            WHERE o.provider.id = :providerId
            AND o.createdAt >= :from
            AND o.createdAt < :to
            """)
    BigDecimal sumTotalForProviderBetween(
            @Param("providerId") Long providerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT COUNT(o) FROM PurchaseOrder o
            WHERE o.createdAt >= :from
            AND o.createdAt < :to
            """)
    long countBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM PurchaseOrder o
            WHERE o.createdAt >= :from
            AND o.createdAt < :to
            """)
    BigDecimal sumTotalBetween(@Param("from") Instant from, @Param("to") Instant to);
}
