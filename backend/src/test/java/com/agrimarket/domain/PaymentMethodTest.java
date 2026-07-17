package com.agrimarket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaymentMethodTest {

    @Test
    void defaultAccepted_isCashEftAndPeach() {
        assertThat(PaymentMethod.defaultAccepted())
                .containsExactlyInAnyOrder(PaymentMethod.CASH, PaymentMethod.EFT, PaymentMethod.PEACH);
    }

    @Test
    void normalizeAccepted_preservesManualEft() {
        Set<PaymentMethod> out = PaymentMethod.normalizeAccepted(EnumSet.of(PaymentMethod.EFT));
        assertThat(out).containsExactly(PaymentMethod.EFT);
    }

    @Test
    void normalizeAccepted_mapsLegacyBothToCashAndManualEft() {
        Set<PaymentMethod> out = PaymentMethod.normalizeAccepted(EnumSet.of(PaymentMethod.BOTH));
        assertThat(out).containsExactlyInAnyOrder(PaymentMethod.CASH, PaymentMethod.EFT);
    }

    @Test
    void normalizeAccepted_preservesCashEftAndPeach() {
        Set<PaymentMethod> out = PaymentMethod.normalizeAccepted(
                EnumSet.of(PaymentMethod.CASH, PaymentMethod.EFT, PaymentMethod.PEACH));
        assertThat(out)
                .containsExactlyInAnyOrder(PaymentMethod.CASH, PaymentMethod.EFT, PaymentMethod.PEACH);
    }

    @Test
    void isCheckoutSelectable_includesCashEftAndPeach() {
        assertThat(PaymentMethod.CASH.isCheckoutSelectable()).isTrue();
        assertThat(PaymentMethod.EFT.isCheckoutSelectable()).isTrue();
        assertThat(PaymentMethod.PEACH.isCheckoutSelectable()).isTrue();
        assertThat(PaymentMethod.BOTH.isCheckoutSelectable()).isFalse();
    }

    @Test
    void fromJson_parsesEftAndRoutesLegacyCardToPeach() {
        assertThat(PaymentMethod.fromJson("EFT")).isEqualTo(PaymentMethod.EFT);
        assertThat(PaymentMethod.fromJson("CARD")).isEqualTo(PaymentMethod.PEACH);
        assertThat(PaymentMethod.fromJson("PEACH")).isEqualTo(PaymentMethod.PEACH);
    }

    @Test
    void peachSubmethodsMapToHostedCheckoutValues() {
        assertThat(PeachPaymentMethod.CARD.hostedCheckoutValue()).isEqualTo("CARD");
        assertThat(PeachPaymentMethod.EFT.hostedCheckoutValue()).isEqualTo("PAYBYBANK");
    }
}
