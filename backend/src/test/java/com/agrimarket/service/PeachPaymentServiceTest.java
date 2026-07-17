package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.domain.PeachPaymentMethod;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PeachPaymentServiceTest {

    @Test
    void cardIsForcedAsTheOnlyHostedCheckoutMethod() {
        Map<String, String> fields = new LinkedHashMap<>();

        PeachPaymentService.applyHostedPaymentMethod(fields, PeachPaymentMethod.CARD);

        assertThat(fields)
                .containsEntry("defaultPaymentMethod", "CARD")
                .containsEntry("forceDefaultMethod", "true");
    }

    @Test
    void instantEftMapsToPayByBankAndIsForced() {
        Map<String, String> fields = new LinkedHashMap<>();

        PeachPaymentService.applyHostedPaymentMethod(fields, PeachPaymentMethod.EFT);

        assertThat(fields)
                .containsEntry("defaultPaymentMethod", "PAYBYBANK")
                .containsEntry("forceDefaultMethod", "true");
    }
}
