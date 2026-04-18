package com.agrimarket.repo;

import com.agrimarket.domain.CartSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartSessionRepository extends JpaRepository<CartSession, Long> {

    Optional<CartSession> findBySessionKey(String sessionKey);

    @Query(
            "SELECT c FROM CartSession c LEFT JOIN FETCH c.lines l LEFT JOIN FETCH l.listing li LEFT JOIN FETCH li.provider LEFT JOIN FETCH li.category WHERE c.sessionKey = :key")
    Optional<CartSession> findBySessionKeyWithLines(@Param("key") String sessionKey);
}
