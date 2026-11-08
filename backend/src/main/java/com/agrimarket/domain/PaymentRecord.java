package com.agrimarket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_booking_id")
    private RentalBooking rentalBooking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentRecordStatus status = PaymentRecordStatus.PENDING_PAYMENT;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Peach checkoutId returned when the Hosted Checkout session was created. */
    @Column(name = "gateway_checkout_id", length = 64)
    private String gatewayCheckoutId;

    /** Peach merchantTransactionId (8-16 chars) — shared across all payment records in one cart checkout. */
    @Column(name = "gateway_merchant_ref", length = 32)
    private String gatewayMerchantRef;

    /** Latest Peach result code, retained so return-page polling can report cancellation/uncertainty. */
    @Column(name = "gateway_result_code", length = 32)
    private String gatewayResultCode;

    /** Gateway that handled an online payment (null for cash / manual EFT and for older rows). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "gateway", length = 16)
    private PaymentGateway gateway;

    // Seller settlement (online payments only). Recorded when the gateway confirms payment.

    /** When the gateway confirmed an online payment. */
    @Column(name = "paid_at")
    private Instant paidAt;

    /** Platform fee percent in force when the payment was confirmed. */
    @Column(name = "platform_fee_percent", precision = 5, scale = 2)
    private BigDecimal platformFeePercent;

    @Column(name = "platform_fee", precision = 14, scale = 2)
    private BigDecimal platformFee;

    /** Amount owed to the seller: amount minus platform fee. */
    @Column(name = "seller_payout_amount", precision = 14, scale = 2)
    private BigDecimal sellerPayoutAmount;

    /** Null on rows created before settlement existed; read through {@link #getSettlementStatus()} as NONE. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "settlement_status", length = 16)
    private SettlementStatus settlementStatus = SettlementStatus.NONE;

    /** When the seller was paid out (or, for REFUNDED, when the buyer was refunded). */
    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    /** Admin who recorded the payout / refund. */
    @Column(name = "paid_out_by", length = 254)
    private String paidOutBy;

    /** Bank transfer reference of the payout / refund. */
    @Column(name = "payout_reference", length = 120)
    private String payoutReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SettlementStatus getSettlementStatus() {
        return settlementStatus == null ? SettlementStatus.NONE : settlementStatus;
    }
}

