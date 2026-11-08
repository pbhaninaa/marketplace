package com.agrimarket.api;

import com.agrimarket.api.dto.PlatformSettingsResponse;
import com.agrimarket.api.dto.PlatformSettingsUpdateRequest;
import com.agrimarket.domain.PlatformSettings;
import com.agrimarket.service.OnlinePaymentGatewayResolver;
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
    private final OnlinePaymentGatewayResolver gatewayResolver;

    @GetMapping
    public PlatformSettingsResponse get() {
        return toResponse(settingsService.getOrCreate());
    }

    @PutMapping
    public PlatformSettingsResponse update(@Valid @RequestBody PlatformSettingsUpdateRequest req) {
        return toResponse(settingsService.update(req));
    }

    private PlatformSettingsResponse toResponse(PlatformSettings s) {
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
                s.getUpdatedAt(),
                s.getOnlinePaymentGateway().name(),
                s.getOnlineOrderFeePercent(),
                gatewayResolver.activeGateway().map(Enum::name).orElse(null),
                gatewayResolver.isPayFastConfigured(),
                gatewayResolver.isPeachConfigured());
    }
}
