package com.agrimarket.service;

import com.agrimarket.api.dto.ProviderSettingsResponse;
import com.agrimarket.api.dto.ProviderSettingsUpdateRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.Provider;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderSettingsService {

    private final ProviderRepository providerRepository;

    @Transactional(readOnly = true)
    public ProviderSettingsResponse get(MarketUserPrincipal actor) {
        Provider p = providerRepository
                .findById(actor.getProviderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        Set<PaymentMethod> accepted = EnumSet.allOf(PaymentMethod.class);
        if (p.getAcceptedPaymentMethods() != null && !p.getAcceptedPaymentMethods().isEmpty()) {
            accepted = EnumSet.copyOf(p.getAcceptedPaymentMethods());
        }
        return new ProviderSettingsResponse(
                p.getId(),
                p.getName(),
                p.getLocation(),
                p.getBankName(),
                p.getBankAccountName(),
                p.getBankAccountNumber(),
                p.getBankBranchCode(),
                p.getBankReference(),
                accepted);
    }

    @Transactional
    public ProviderSettingsResponse update(MarketUserPrincipal actor, ProviderSettingsUpdateRequest req) {
        Provider p = providerRepository
                .findById(actor.getProviderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));

        p.setLocation(req.location());
        p.setBankName(blankToNull(req.bankName()));
        p.setBankAccountName(blankToNull(req.bankAccountName()));
        p.setBankAccountNumber(blankToNull(req.bankAccountNumber()));
        p.setBankBranchCode(blankToNull(req.bankBranchCode()));
        p.setBankReference(blankToNull(req.bankReference()));

        if (req.acceptedPaymentMethods() == null || req.acceptedPaymentMethods().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_METHODS_REQUIRED",
                    "Select at least one payment method (EFT and/or CASH).");
        }
        Set<PaymentMethod> accepted = EnumSet.copyOf(req.acceptedPaymentMethods());
        p.setAcceptedPaymentMethods(accepted);

        providerRepository.save(p);
        return get(actor);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

