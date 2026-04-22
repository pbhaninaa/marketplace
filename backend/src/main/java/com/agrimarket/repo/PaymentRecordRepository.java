package com.agrimarket.repo;

import com.agrimarket.domain.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    java.util.Optional<PaymentRecord> findByPurchaseOrder_Id(Long purchaseOrderId);

    java.util.Optional<PaymentRecord> findByRentalBooking_Id(Long rentalBookingId);
}
