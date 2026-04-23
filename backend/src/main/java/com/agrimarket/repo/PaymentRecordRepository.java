package com.agrimarket.repo;

import com.agrimarket.domain.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    java.util.Optional<PaymentRecord> findByPurchaseOrder_Id(Long purchaseOrderId);

    java.util.Optional<PaymentRecord> findByRentalBooking_Id(Long rentalBookingId);

    @Modifying
    @Query("DELETE FROM PaymentRecord pr WHERE pr.purchaseOrder.id = :purchaseOrderId")
    void deleteByPurchaseOrder_Id(@Param("purchaseOrderId") Long purchaseOrderId);
}
