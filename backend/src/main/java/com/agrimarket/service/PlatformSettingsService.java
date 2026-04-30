package com.agrimarket.service;

import com.agrimarket.api.dto.PlatformSettingsUpdateRequest;
import com.agrimarket.config.BankDetailsProperties;
import com.agrimarket.config.SubscriptionPricingProperties;
import com.agrimarket.domain.PlatformSettings;
import com.agrimarket.repo.PlatformSettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    public static final long SINGLETON_ID = 1L;

    private final PlatformSettingsRepository repository;
    private final BankDetailsProperties bankDefaults;
    private final SubscriptionPricingProperties pricingDefaults;

    @Transactional(readOnly = true)
    public PlatformSettings getOrCreate() {
        return repository.findById(SINGLETON_ID).orElseGet(() -> {
            PlatformSettings s = new PlatformSettings();
            s.setId(SINGLETON_ID);
            // Defaults from env/properties (can be overridden in UI)
            s.setBankName(bankDefaults.bankName());
            s.setAccountName(bankDefaults.accountName());
            s.setAccountNumber(bankDefaults.accountNumber());
            s.setBranchCode(bankDefaults.branchCode());
            s.setReferenceHint(bankDefaults.referenceHint());
            s.setBasicMonthly(pricingDefaults.basicMonthly());
            s.setPremiumMonthly(pricingDefaults.premiumMonthly());
            s.setUsageFeePercent(BigDecimal.ZERO);
            s.setUpdatedAt(Instant.now());
            return repository.save(s);
        });
    }

    @Transactional
    public PlatformSettings update(PlatformSettingsUpdateRequest req) {
        PlatformSettings s = getOrCreate();
        s.setSystemName(req.systemName());
        s.setBankName(req.bankName());
        s.setAccountName(req.accountName());
        s.setAccountNumber(req.accountNumber());
        s.setBranchCode(req.branchCode());
        s.setReferenceHint(req.referenceHint());
        s.setBasicMonthly(req.basicMonthly());
        s.setPremiumMonthly(req.premiumMonthly());
        s.setUsageFeePercent(req.usageFeePercent() == null ? BigDecimal.ZERO : req.usageFeePercent());
        s.setInvoiceLegalName(trimToNull(req.invoiceLegalName()));
        s.setInvoiceAddress(trimToNull(req.invoiceAddress()));
        s.setInvoiceVatNumber(trimToNull(req.invoiceVatNumber()));
        s.setInvoiceFooterNote(trimToNull(req.invoiceFooterNote()));
        s.setUpdatedAt(Instant.now());
        return repository.save(s);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

