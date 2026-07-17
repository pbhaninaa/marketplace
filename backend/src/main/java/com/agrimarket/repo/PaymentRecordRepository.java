package com.agrimarket.repo;

import com.agrimarket.domain.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    java.util.Optional<PaymentRecord> findByOrder_Id(Long orderId);

    java.util.Optional<PaymentRecord> findByRentalBooking_Id(Long rentalBookingId);

    /** All payment records sharing a Peach merchantTransactionId (one Hosted Checkout can cover order + rentals). */
    java.util.List<PaymentRecord> findAllByGatewayMerchantRef(String gatewayMerchantRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM PaymentRecord pr WHERE pr.gatewayMerchantRef = :gatewayMerchantRef ORDER BY pr.id")
    java.util.List<PaymentRecord> findAllByGatewayMerchantRefForUpdate(
            @Param("gatewayMerchantRef") String gatewayMerchantRef);

    java.util.Optional<PaymentRecord> findByGatewayCheckoutId(String gatewayCheckoutId);

    @Modifying
    @Query("DELETE FROM PaymentRecord pr WHERE pr.order.id = :orderId")
    void deleteByOrder_Id(@Param("orderId") Long orderId);

    @Modifying
    @Query("DELETE FROM PaymentRecord pr WHERE pr.rentalBooking.id = :rentalBookingId")
    void deleteByRentalBooking_Id(@Param("rentalBookingId") Long rentalBookingId);
}

