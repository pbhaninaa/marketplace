package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agrimarket.config.PayFastProperties;
import com.agrimarket.config.PeachProperties;
import com.agrimarket.domain.OnlineGatewayPreference;
import com.agrimarket.domain.PaymentGateway;
import com.agrimarket.domain.PlatformSettings;
import com.agrimarket.repo.PlatformSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OnlinePaymentGatewayResolverTest {

    @Test
    void autoPrefersPayFastThenPeach() {
        assertThat(OnlinePaymentGatewayResolver.resolve(OnlineGatewayPreference.AUTO, true, true))
                .contains(PaymentGateway.PAYFAST);
        assertThat(OnlinePaymentGatewayResolver.resolve(OnlineGatewayPreference.AUTO, true, false))
                .contains(PaymentGateway.PAYFAST);
        assertThat(OnlinePaymentGatewayResolver.resolve(OnlineGatewayPreference.AUTO, false, true))
                .contains(PaymentGateway.PEACH);
        assertThat(OnlinePaymentGatewayResolver.resolve(OnlineGatewayPreference.AUTO, false, false)).isEmpty();
        assertThat(OnlinePaymentGatewayResolver.resolve(null, false, true)).contains(PaymentGateway.PEACH);
    }

    @Test
    void explicitChoiceIsStrict() {
        assertThat(OnlinePaymentGatewayResolver.resolve(OnlineGatewayPreference.PAYFAST, true, true))
                .contains(PaymentGateway.PAYFAST);
        assertThat(OnlinePaymentGatewayResolver.resolve(OnlineGatewayPreference.PAYFAST, false, true)).isEmpty();
        assertThat(OnlinePaymentGatewayResolver.resolve(OnlineGatewayPreference.PEACH, true, true))
                .contains(PaymentGateway.PEACH);
        assertThat(OnlinePaymentGatewayResolver.resolve(OnlineGatewayPreference.PEACH, true, false)).isEmpty();
    }

    @Test
    void readsTheAdminSettingAndConfiguredGateways() {
        PayFastProperties payFast = new PayFastProperties();
        payFast.setEnabled(true);
        payFast.setMerchantId("10000100");
        payFast.setMerchantKey("46f0cd694581a");
        payFast.setPublicBaseUrl("https://api.example.com");
        PeachProperties peach = new PeachProperties();
        ReflectionTestUtils.setField(peach, "enabled", true);
        ReflectionTestUtils.setField(peach, "clientId", "c");
        ReflectionTestUtils.setField(peach, "clientSecret", "s");
        ReflectionTestUtils.setField(peach, "merchantId", "m");
        ReflectionTestUtils.setField(peach, "entityId", "e");
        ReflectionTestUtils.setField(peach, "secretToken", "t");

        PlatformSettingsRepository repo = mock(PlatformSettingsRepository.class);
        PlatformSettings settings = new PlatformSettings();
        when(repo.findById(PlatformSettingsService.SINGLETON_ID)).thenReturn(Optional.of(settings));
        OnlinePaymentGatewayResolver resolver = new OnlinePaymentGatewayResolver(peach, payFast, repo);

        assertThat(resolver.activeGateway()).contains(PaymentGateway.PAYFAST);
        settings.setOnlinePaymentGateway(OnlineGatewayPreference.PEACH);
        assertThat(resolver.activeGateway()).contains(PaymentGateway.PEACH);
        settings.setOnlinePaymentGateway(null); // older row: AUTO
        assertThat(resolver.activeGateway()).contains(PaymentGateway.PAYFAST);
        payFast.setPublicBaseUrl(""); // not configured without a notify URL base
        assertThat(resolver.activeGateway()).contains(PaymentGateway.PEACH);
        assertThat(resolver.isOnlineAvailable()).isTrue();
    }
}
