package com.agrimarket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
public class PlatformSettings {

    /**
     * Singleton row. We always use ID=1.
     */
    @Id
    private Long id = 1L;

    @Column(name = "system_name", length = 120, nullable = false)
    private String systemName = "Agri Marketplace";

    // Admin banking details for provider subscriptions (EFT)
    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "bank_account_name", length = 200)
    private String accountName;

    @Column(name = "bank_account_number", length = 80)
    private String accountNumber;

    @Column(name = "bank_branch_code", length = 40)
    private String branchCode;

    @Column(name = "bank_reference_hint", length = 200)
    private String referenceHint;

    // Subscription prices (monthly)
    @Column(name = "basic_monthly", precision = 14, scale = 2)
    private BigDecimal basicMonthly;

    @Column(name = "premium_monthly", precision = 14, scale = 2)
    private BigDecimal premiumMonthly;

    /**
     * Platform usage fee as a percentage of each paid order total (provider-confirmed PAID).
     * First-month amount: base plan price + sum(order totals × usageFeePercent / 100) over paid orders in the usage window.
     */
    @Column(name = "usage_fee_per_transaction", precision = 14, scale = 2)
    private BigDecimal usageFeePercent = BigDecimal.ZERO;

    /** Shown on provider order invoices; falls back to system name when blank. */
    @Column(name = "invoice_legal_name", length = 200)
    private String invoiceLegalName;

    @Column(name = "invoice_address", length = 2000)
    private String invoiceAddress;

    @Column(name = "invoice_vat_number", length = 80)
    private String invoiceVatNumber;

    @Column(name = "invoice_footer_note", length = 2000)
    private String invoiceFooterNote;

    /** Which gateway serves the online "Card / Instant EFT" option. Null on older rows means AUTO. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "online_payment_gateway", length = 16)
    private OnlineGatewayPreference onlinePaymentGateway = OnlineGatewayPreference.AUTO;

    /**
     * Platform fee (percent) kept from each online order / rental payment before the seller is paid
     * out. Separate from {@link #usageFeePercent}, which is billed on the provider's subscription.
     */
    @Column(name = "online_order_fee_percent", precision = 5, scale = 2)
    private BigDecimal onlineOrderFeePercent = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public OnlineGatewayPreference getOnlinePaymentGateway() {
        return onlinePaymentGateway == null ? OnlineGatewayPreference.AUTO : onlinePaymentGateway;
    }

    public BigDecimal getOnlineOrderFeePercent() {
        return onlineOrderFeePercent == null ? BigDecimal.ZERO : onlineOrderFeePercent;
    }
}

