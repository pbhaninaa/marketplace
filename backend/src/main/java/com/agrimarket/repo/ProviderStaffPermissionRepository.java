package com.agrimarket.repo;

import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.ProviderStaffPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderStaffPermissionRepository extends JpaRepository<ProviderStaffPermission, Long> {

    @Query(
            "SELECT p.permissionKey FROM ProviderStaffPermission p WHERE p.provider.id = :providerId AND p.user.id = :userId")
    List<ProviderPermissionKey> findKeys(@Param("providerId") Long providerId, @Param("userId") Long userId);

    void deleteByProvider_IdAndUser_Id(Long providerId, Long userId);
}

