package com.agrimarket.repo;

import com.agrimarket.domain.AppNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<AppNotification> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndReadFalse(Long userId);

    Optional<AppNotification> findByIdAndUser_Id(Long id, Long userId);
}
