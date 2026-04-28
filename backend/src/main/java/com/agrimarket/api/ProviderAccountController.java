package com.agrimarket.api;

import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me/account")
@RequiredArgsConstructor
public class ProviderAccountController {

    private final ProviderAccountService providerAccountService;

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal MarketUserPrincipal actor) {
        providerAccountService.deactivateProviderAccount(actor);
    }
}

