package com.agrimarket.repo;

import com.agrimarket.domain.CartLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CartLineRepository extends JpaRepository<CartLine, Long> {
    void deleteByListing_Id(Long listingId);
    boolean existsByListing_Id(Long listingId);
    List<CartLine> findByOrderId(Long orderId);
}

