package com.agrimarket.repo;

import com.agrimarket.domain.StaffPayrollEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffPayrollEntryRepository extends JpaRepository<StaffPayrollEntry, Long> {

    List<StaffPayrollEntry> findByProvider_IdOrderByCreatedAtDesc(Long providerId);

    List<StaffPayrollEntry> findByProvider_IdAndStaff_IdOrderByCreatedAtDesc(Long providerId, Long staffId);
}
