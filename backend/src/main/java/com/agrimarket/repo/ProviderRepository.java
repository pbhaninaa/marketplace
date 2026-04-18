package com.agrimarket.repo;

import com.agrimarket.domain.Provider;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findBySlug(String slug);

    @Query(
            """
            SELECT COUNT(p) FROM Provider p
            WHERE p.createdAt >= :from
            AND p.createdAt < :to
            """)
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
