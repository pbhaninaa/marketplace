package com.agrimarket.repo;

import com.agrimarket.domain.CartLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CartLineRepository extends JpaRepository<CartLine, Long> {
    void deleteByListing_Id(Long listingId);
    boolean existsByListing_Id(Long listingId);
    boolean existsByListing_IdAndOrderIsNotNull(Long listingId);
    boolean existsByListing_IdAndCartSessionIsNotNull(Long listingId);
    List<CartLine> findByOrderId(Long orderId);

    @Modifying
    @Query("DELETE FROM CartLine cl WHERE cl.listing.id = :listingId AND cl.order IS NULL AND cl.cartSession IS NOT NULL")
    void deleteActiveCartLinesByListingId(@Param("listingId") Long listingId);
}

