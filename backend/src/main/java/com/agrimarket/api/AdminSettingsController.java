package com.agrimarket.api;

import com.agrimarket.api.dto.PlatformSettingsResponse;
import com.agrimarket.api.dto.PlatformSettingsUpdateRequest;
import com.agrimarket.service.PlatformSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PlatformSettingsService settingsService;

    @GetMapping
    public PlatformSettingsResponse get() {
        var s = settingsService.getOrCreate();
        return new PlatformSettingsResponse(
                s.getSystemName(),
                s.getBankName(),
                s.getAccountName(),
                s.getAccountNumber(),
                s.getBranchCode(),
                s.getReferenceHint(),
                s.getBasicMonthly(),
                s.getPremiumMonthly(),
                s.getUsageFeePercent(),
                s.getInvoiceLegalName(),
                s.getInvoiceAddress(),
                s.getInvoiceVatNumber(),
                s.getInvoiceFooterNote(),
                s.getUpdatedAt());
    }

    @PutMapping
    public PlatformSettingsResponse update(@Valid @RequestBody PlatformSettingsUpdateRequest req) {
        var s = settingsService.update(req);
        return new PlatformSettingsResponse(
                s.getSystemName(),
                s.getBankName(),
                s.getAccountName(),
                s.getAccountNumber(),
                s.getBranchCode(),
                s.getReferenceHint(),
                s.getBasicMonthly(),
                s.getPremiumMonthly(),
                s.getUsageFeePercent(),
                s.getInvoiceLegalName(),
                s.getInvoiceAddress(),
                s.getInvoiceVatNumber(),
                s.getInvoiceFooterNote(),
                s.getUpdatedAt());
    }
}

