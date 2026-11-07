package com.agrimarket.repo;

import com.agrimarket.domain.ClientOtpChallenge;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientOtpChallengeRepository extends JpaRepository<ClientOtpChallenge, Long> {

    Optional<ClientOtpChallenge> findTopByTargetOrderByCreatedAtDesc(String target);

    Optional<ClientOtpChallenge> findTopByTargetAndPurposeOrderByCreatedAtDesc(String target, String purpose);

    void deleteByTargetAndPurpose(String target, String purpose);
}
