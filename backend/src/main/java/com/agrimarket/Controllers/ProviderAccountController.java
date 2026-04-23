package com.agrimarket.api;

import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderAccountService;
import com.agrimarket.service.TenantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me/account")
@RequiredArgsConstructor
public class ProviderAccountController {

    private final ProviderAccountService providerAccountService;

    @DeleteMapping
    public void deactivate(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        providerAccountService.deactivateProviderAccount(user);
    }
}

