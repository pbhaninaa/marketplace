package com.agrimarket.repo;

import com.agrimarket.domain.Listing;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {
    List<Listing> findAllByProvider_Id(Long providerId);
    void deleteByIdAndProvider_Id(Long id, Long providerId);
    Optional<Listing> findById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Listing l WHERE l.id = :id")
    Optional<Listing> findByIdWithLock(@Param("id") Long id);

    @Query(
            """
            SELECT COUNT(l) FROM Listing l
            WHERE l.createdAt >= :from
            AND l.createdAt < :to
            """)
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);

    long countByActiveTrue();
}
