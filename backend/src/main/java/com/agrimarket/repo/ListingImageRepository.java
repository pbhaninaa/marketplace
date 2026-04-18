package com.agrimarket.repo;

import com.agrimarket.domain.ListingImage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {
    Optional<ListingImage> findByIdAndProvider_Id(Long id, Long providerId);
}
