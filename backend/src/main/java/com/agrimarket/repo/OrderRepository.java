package com.agrimarket.repo;

import com.agrimarket.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"provider", "lines", "lines.listing"})
    Page<Order> findByProvider_IdOrderByCreatedAtDesc(Long providerId, Pageable pageable);

    Optional<Order> findByVerificationCode(String verificationCode);

    Optional<Order> findBySessionKey(String sessionKey);

    @Query(
            """
            SELECT COUNT(o) FROM Order o
            WHERE o.provider.id = :providerId
            AND o.createdAt >= :from
            AND o.createdAt < :to
            """)
    long countForProviderBetween(
            @Param("providerId") Long providerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.provider.id = :providerId
            AND o.createdAt >= :from
            AND o.createdAt < :to
            """)
    BigDecimal sumTotalForProviderBetween(
            @Param("providerId") Long providerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT COUNT(o) FROM Order o
            WHERE o.createdAt >= :from
            AND o.createdAt < :to
            """)
    long countBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.createdAt >= :from
            AND o.createdAt < :to
            """)
    BigDecimal sumTotalBetween(@Param("from") Instant from, @Param("to") Instant to);

    java.util.List<Order> findByProvider_IdAndStatusIn(
            Long providerId,
            java.util.Collection<com.agrimarket.domain.OrderStatus> statuses);
}