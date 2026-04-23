package com.agrimarket.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ORDER REPOSITORY WITH OPTIMIZED QUERIES
 *
 * Provides data access methods for order management with proper indexing
 * and query optimization for the state machine operations.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // ============================================================================
    // BASIC QUERY METHODS
    // ============================================================================

    /**
     * Finds an order by ID with provider authorization check.
     */
    Optional<Order> findByIdAndProviderId(UUID id, UUID providerId);

    /**
     * Finds all orders for a provider, ordered by creation date (newest first).
     */
    List<Order> findByProviderIdOrderByCreatedAtDesc(UUID providerId);

    /**
     * Finds orders by provider and status, ordered by creation date (newest first).
     */
    List<Order> findByProviderIdAndStatusOrderByCreatedAtDesc(UUID providerId, OrderStatus status);

    // ============================================================================
    // VERIFICATION QUERIES
    // ============================================================================

    /**
     * Finds an order by verification code (for guest verification).
     * Used when guest provides code to provider.
     */
    Optional<Order> findByVerificationCode(String verificationCode);

    /**
     * Checks if a verification code exists and is not yet used.
     */
    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.verificationCode = :code AND o.verifiedAt IS NULL")
    boolean existsUnusedVerificationCode(@Param("code") String code);

    // ============================================================================
    // AGGREGATION QUERIES
    // ============================================================================

    /**
     * Counts orders by status for a provider.
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.providerId = :providerId AND o.status = :status")
    long countByProviderIdAndStatus(@Param("providerId") UUID providerId, @Param("status") OrderStatus status);

    /**
     * Gets order statistics for a provider.
     */
    @Query("""
                SELECT new com.agrimarket.order.OrderStats(
                    COUNT(o),
                    SUM(CASE WHEN o.status = 'FULFILLED' THEN o.totalAmount ELSE 0 END),
                    AVG(CASE WHEN o.status = 'FULFILLED' THEN o.totalAmount ELSE NULL END)
                )
                FROM Order o
                WHERE o.providerId = :providerId
                AND o.createdAt >= :since
            """)
    Optional<OrderStats> getOrderStats(@Param("providerId") UUID providerId,
            @Param("since") java.time.LocalDateTime since);

    // ============================================================================
    // INVENTORY-RELATED QUERIES
    // ============================================================================

    /**
     * Finds orders that have reserved inventory (PENDING_PAYMENT or VERIFIED
     * status).
     * Used for inventory reconciliation.
     */
    @Query("SELECT o FROM Order o WHERE o.listingId = :listingId AND o.status IN ('PENDING_PAYMENT', 'VERIFIED')")
    List<Order> findReservedOrdersByListingId(@Param("listingId") UUID listingId);

    /**
     * Finds orders that have deducted inventory (PAID or FULFILLED status).
     * Used for inventory reconciliation.
     */
    @Query("SELECT o FROM Order o WHERE o.listingId = :listingId AND o.status IN ('PAID', 'FULFILLED')")
    List<Order> findDeductedOrdersByListingId(@Param("listingId") UUID listingId);

    // ============================================================================
    // AUDIT TRAIL QUERIES
    // ============================================================================

    /**
     * Finds recent status changes for audit purposes.
     */
    @Query("SELECT o FROM Order o WHERE o.statusChangedAt >= :since ORDER BY o.statusChangedAt DESC")
    List<Order> findRecentStatusChanges(@Param("since") java.time.LocalDateTime since);

    /**
     * Finds orders that transitioned to a specific status within a time range.
     */
    @Query("""
                SELECT o FROM Order o
                WHERE o.status = :status
                AND o.statusChangedAt BETWEEN :start AND :end
                ORDER BY o.statusChangedAt DESC
            """)
    List<Order> findOrdersByStatusTransition(
            @Param("status") OrderStatus status,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);
}