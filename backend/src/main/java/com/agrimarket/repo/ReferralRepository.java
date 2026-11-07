package com.agrimarket.repo;

import com.agrimarket.domain.Referral;
import com.agrimarket.domain.ReferralStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    Optional<Referral> findByRefereeProvider_Id(Long providerId);

    @EntityGraph(attributePaths = {"refereeProvider"})
    List<Referral> findByReferrer_IdOrderByCreatedAtDesc(Long referrerUserId);

    long countByReferrer_Id(Long referrerUserId);

    long countByReferrer_IdAndStatusIn(Long referrerUserId, List<ReferralStatus> statuses);

    long countByStatus(ReferralStatus status);

    @Query("SELECT COALESCE(SUM(r.commissionAmount), 0) FROM Referral r WHERE r.referrer.id = :userId AND r.status = :status")
    BigDecimal sumCommissionForReferrer(@Param("userId") Long userId, @Param("status") ReferralStatus status);

    @Query("SELECT COALESCE(SUM(r.commissionAmount), 0) FROM Referral r WHERE r.status = :status")
    BigDecimal sumCommission(@Param("status") ReferralStatus status);

    /**
     * Admin listing. {@code referrer} / {@code referee} are lower-cased LIKE patterns (already
     * wrapped in {@code %...%}) matched against email, display name, business name or code;
     * {@code from} (inclusive) / {@code to} (exclusive) bound the referral date.
     */
    @EntityGraph(attributePaths = {"referrer", "referrerProvider", "refereeProvider", "refereeUser"})
    @Query(
            """
            SELECT r FROM Referral r
            LEFT JOIN r.referrer ru
            LEFT JOIN r.referrerProvider rp
            LEFT JOIN r.refereeProvider ep
            LEFT JOIN r.refereeUser eu
            WHERE (:status IS NULL OR r.status = :status)
              AND (:from IS NULL OR r.createdAt >= :from)
              AND (:to IS NULL OR r.createdAt < :to)
              AND (:referrer IS NULL
                   OR LOWER(ru.email) LIKE :referrer
                   OR LOWER(COALESCE(ru.displayName, '')) LIKE :referrer
                   OR LOWER(COALESCE(rp.name, '')) LIKE :referrer
                   OR LOWER(r.referralCode) LIKE :referrer)
              AND (:referee IS NULL
                   OR LOWER(COALESCE(eu.email, '')) LIKE :referee
                   OR LOWER(ep.name) LIKE :referee)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    Page<Referral> search(
            @Param("status") ReferralStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("referrer") String referrer,
            @Param("referee") String referee,
            Pageable pageable);
}
