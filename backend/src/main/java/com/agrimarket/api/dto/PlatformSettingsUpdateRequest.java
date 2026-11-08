package com.agrimarket.api.dto;

import com.agrimarket.util.validation.BankAccountNumber;
import com.agrimarket.util.validation.BranchCode;
import com.agrimarket.util.validation.BusinessName;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PlatformSettingsUpdateRequest(
        @NotBlank @Size(max = 120) String systemName,
        @Size(max = 200) @BusinessName String bankName,
        @Size(max = 200) @BusinessName String accountName,
        @BankAccountNumber String accountNumber,
        @BranchCode String branchCode,
        @Size(max = 200) String referenceHint,
        @DecimalMin("0.00") @DecimalMax("10000000") BigDecimal basicMonthly,
        @DecimalMin("0.00") @DecimalMax("10000000") BigDecimal premiumMonthly,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal usageFeePercent,
        @Size(max = 200) @BusinessName String invoiceLegalName,
        @Size(max = 2000) String invoiceAddress,
        @Size(max = 80) String invoiceVatNumber,
        @Size(max = 2000) String invoiceFooterNote,
        /** AUTO, PAYFAST or PEACH. Null keeps the current choice (older clients). */
        com.agrimarket.domain.OnlineGatewayPreference onlinePaymentGateway,
        /** Platform fee percent on online order / rental payments. Null keeps the current value. */
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal onlineOrderFeePercent) {}
