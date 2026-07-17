package com.agrimarket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaymentMethodTest {

    @Test
    void defaultAccepted_isCashAndPeach() {
        assertThat(PaymentMethod.defaultAccepted()).containsExactlyInAnyOrder(PaymentMethod.CASH, PaymentMethod.PEACH);
    }

    @Test
    void normalizeAccepted_mapsLegacyEftToPeach() {
        Set<PaymentMethod> out = PaymentMethod.normalizeAccepted(EnumSet.of(PaymentMethod.EFT));
        assertThat(out).containsExactly(PaymentMethod.PEACH);
    }

    @Test
    void normalizeAccepted_mapsLegacyBothToCashAndPeach() {
        Set<PaymentMethod> out = PaymentMethod.normalizeAccepted(EnumSet.of(PaymentMethod.BOTH));
        assertThat(out).containsExactlyInAnyOrder(PaymentMethod.CASH, PaymentMethod.PEACH);
    }

    @Test
    void normalizeAccepted_preservesCashAndPeach() {
        Set<PaymentMethod> out =
                PaymentMethod.normalizeAccepted(EnumSet.of(PaymentMethod.CASH, PaymentMethod.PEACH));
        assertThat(out).containsExactlyInAnyOrder(PaymentMethod.CASH, PaymentMethod.PEACH);
    }

    @Test
    void isCheckoutSelectable_includesOnlyCashAndPeach() {
        assertThat(PaymentMethod.CASH.isCheckoutSelectable()).isTrue();
        assertThat(PaymentMethod.EFT.isCheckoutSelectable()).isFalse();
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
