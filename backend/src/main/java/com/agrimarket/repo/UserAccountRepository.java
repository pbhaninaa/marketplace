package com.agrimarket.repo;

import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    @Query("SELECT u FROM UserAccount u LEFT JOIN FETCH u.provider WHERE LOWER(u.email) = LOWER(:email)")
    Optional<UserAccount> findByEmailForAuth(@Param("email") String email);

    boolean existsByRole(UserRole role);

    java.util.List<UserAccount> findByProvider_IdOrderByEmailAsc(Long providerId);

    Optional<UserAccount> findByIdAndProvider_Id(Long id, Long providerId);

    List<UserAccount> findByRoleOrderByEmailAsc(UserRole role);

    @Query(
            """
            SELECT COUNT(u) FROM UserAccount u
            WHERE u.createdAt >= :from
            AND u.createdAt < :to
            """)
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
