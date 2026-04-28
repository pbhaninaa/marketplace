package com.agrimarket.repo;

import com.agrimarket.domain.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    java.util.Optional<PaymentRecord> findByOrder_Id(Long orderId);

    java.util.Optional<PaymentRecord> findByRentalBooking_Id(Long rentalBookingId);

    @Modifying
    @Query("DELETE FROM PaymentRecord pr WHERE pr.order.id = :orderId")
    void deleteByOrder_Id(@Param("orderId") Long orderId);

    @Modifying
    @Query("DELETE FROM PaymentRecord pr WHERE pr.rentalBooking.id = :rentalBookingId")
    void deleteByRentalBooking_Id(@Param("rentalBookingId") Long rentalBookingId);
}

