package com.agrimarket.api;

import com.agrimarket.api.dto.SettlementDtos.ProviderPayoutsResponse;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.SellerSettlementService;
import com.agrimarket.service.TenantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of a provider's own online payments: platform fee, amount owed and payout status. */
@RestController
@RequiredArgsConstructor
public class ProviderPayoutsController {

    private final SellerSettlementService settlementService;

    @GetMapping("/api/provider/me/payouts")
    public ProviderPayoutsResponse myPayouts(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        return settlementService.forProvider(user.getProviderId());
    }
}
