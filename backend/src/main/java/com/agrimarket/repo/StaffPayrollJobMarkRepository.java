package com.agrimarket.repo;

import com.agrimarket.domain.StaffPayrollJobMark;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffPayrollJobMarkRepository extends JpaRepository<StaffPayrollJobMark, Long> {

    List<StaffPayrollJobMark> findByStaff_IdAndOrder_IdIn(Long staffUserId, Collection<Long> orderIds);

    Optional<StaffPayrollJobMark> findByStaff_IdAndOrder_Id(Long staffUserId, Long orderId);

    @Modifying(clearAutomatically = true)
    @Query("delete from StaffPayrollJobMark m where m.staff.id = :staffUserId and m.order.id = :orderId")
    void deleteByStaff_IdAndOrder_Id(@Param("staffUserId") Long staffUserId, @Param("orderId") Long orderId);

    boolean existsByStaff_IdAndOrder_Id(Long staffUserId, Long orderId);
}
