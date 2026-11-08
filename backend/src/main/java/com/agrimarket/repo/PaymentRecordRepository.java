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

    /** Settlement listing for the admin payouts page; null filters are ignored. Dates filter on paidAt. */
    @Query("SELECT pr FROM PaymentRecord pr JOIN FETCH pr.provider p "
            + "LEFT JOIN FETCH pr.order LEFT JOIN FETCH pr.rentalBooking "
            + "WHERE pr.settlementStatus = :status "
            + "AND (:providerId IS NULL OR p.id = :providerId) "
            + "AND (:paidFrom IS NULL OR pr.paidAt >= :paidFrom) "
            + "AND (:paidTo IS NULL OR pr.paidAt < :paidTo) "
            + "ORDER BY pr.paidAt ASC, pr.id ASC")
    java.util.List<PaymentRecord> findForSettlement(
            @Param("status") com.agrimarket.domain.SettlementStatus status,
            @Param("providerId") Long providerId,
            @Param("paidFrom") java.time.Instant paidFrom,
            @Param("paidTo") java.time.Instant paidTo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM PaymentRecord pr WHERE pr.settlementStatus = :status AND pr.provider.id = :providerId ORDER BY pr.id")
    java.util.List<PaymentRecord> findForSettlementForUpdate(
            @Param("status") com.agrimarket.domain.SettlementStatus status,
            @Param("providerId") Long providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM PaymentRecord pr WHERE pr.id IN :ids ORDER BY pr.id")
    java.util.List<PaymentRecord> findAllByIdInForUpdate(@Param("ids") java.util.Collection<Long> ids);

    /** A provider's online payments that entered settlement (anything but NONE / null), newest first. */
    @Query("SELECT pr FROM PaymentRecord pr LEFT JOIN FETCH pr.order LEFT JOIN FETCH pr.rentalBooking "
            + "WHERE pr.provider.id = :providerId AND pr.settlementStatus IS NOT NULL AND pr.settlementStatus <> :none "
            + "ORDER BY pr.paidAt DESC, pr.id DESC")
    java.util.List<PaymentRecord> findSettledForProvider(
            @Param("providerId") Long providerId,
            @Param("none") com.agrimarket.domain.SettlementStatus none,
            org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("DELETE FROM PaymentRecord pr WHERE pr.order.id = :orderId")
    void deleteByOrder_Id(@Param("orderId") Long orderId);

    @Modifying
    @Query("DELETE FROM PaymentRecord pr WHERE pr.rentalBooking.id = :rentalBookingId")
    void deleteByRentalBooking_Id(@Param("rentalBookingId") Long rentalBookingId);
}

