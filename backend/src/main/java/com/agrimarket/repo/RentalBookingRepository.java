package com.agrimarket.repo;

import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.RentalBooking;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalBookingRepository extends JpaRepository<RentalBooking, Long> {

    Page<RentalBooking> findByProvider_IdOrderByCreatedAtDesc(Long providerId, Pageable pageable);

    boolean existsByListing_Id(Long listingId);

    Optional<RentalBooking> findByVerificationCode(String verificationCode);

    java.util.List<RentalBooking> findAllBySessionKey(String sessionKey);

    java.util.List<RentalBooking> findAllByListing_Id(Long listingId);

    @Query(
            """
            SELECT COUNT(b) FROM RentalBooking b
            WHERE b.listing.id = :listingId
            AND b.status IN (:statuses)
            AND b.startAt < :end
            AND b.endAt > :start
            """)
    long countOverlapping(
            @Param("listingId") Long listingId,
            @Param("statuses") java.util.Collection<BookingStatus> statuses,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query(
            """
            SELECT b FROM RentalBooking b
            WHERE b.listing.id = :listingId
            AND b.status IN (:statuses)
            AND b.startAt < :end
            AND b.endAt > :start
            ORDER BY b.startAt ASC
            """)
    java.util.List<RentalBooking> findOverlapping(
            @Param("listingId") Long listingId,
            @Param("statuses") java.util.Collection<BookingStatus> statuses,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query(
            """
            SELECT COUNT(b) FROM RentalBooking b
            WHERE b.provider.id = :providerId
            AND b.createdAt >= :from
            AND b.createdAt < :to
            """)
    long countForProviderBetween(
            @Param("providerId") Long providerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT COALESCE(SUM(b.totalAmount), 0) FROM RentalBooking b
            WHERE b.provider.id = :providerId
            AND b.createdAt >= :from
            AND b.createdAt < :to
            """)
    BigDecimal sumTotalForProviderBetween(
            @Param("providerId") Long providerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT COUNT(b) FROM RentalBooking b
            WHERE b.createdAt >= :from
            AND b.createdAt < :to
            """)
    long countBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT COALESCE(SUM(b.totalAmount), 0) FROM RentalBooking b
            WHERE b.createdAt >= :from
            AND b.createdAt < :to
            """)
    BigDecimal sumTotalBetween(@Param("from") Instant from, @Param("to") Instant to);

    java.util.List<RentalBooking> findByProvider_IdAndStatusIn(
            Long providerId,
            java.util.Collection<BookingStatus> statuses);
}
