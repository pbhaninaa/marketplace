package com.agrimarket.repo;

import com.agrimarket.domain.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {
    boolean existsByListing_Id(Long listingId);
}

