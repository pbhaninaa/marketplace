package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.PayFastProperties;
import com.agrimarket.config.PeachProperties;
import com.agrimarket.domain.OnlineGatewayPreference;
import com.agrimarket.domain.PaymentGateway;
import com.agrimarket.domain.PlatformSettings;
import com.agrimarket.repo.PlatformSettingsRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Picks the gateway behind the single online "Card / Instant EFT" option (payment method PEACH),
 * from the admin setting and which gateways are configured. Used by checkout, the cart's accepted
 * methods, provider subscription checkout and the public online-status endpoint.
 *
 * <p>Notifications (webhooks / ITNs) are processed for every configured gateway regardless of this
 * choice, so payments already in flight still complete after the admin switches gateway.
 */
@Service
@RequiredArgsConstructor
public class OnlinePaymentGatewayResolver {

    private final PeachProperties peachProperties;
    private final PayFastProperties payFastProperties;
    private final PlatformSettingsRepository platformSettingsRepository;

    /** The gateway new online payments go through, or empty when online payments are unavailable. */
    public Optional<PaymentGateway> activeGateway() {
        return resolve(preference(), payFastProperties.isConfigured(), peachProperties.isConfigured());
    }

    public boolean isOnlineAvailable() {
        return activeGateway().isPresent();
    }

    public PaymentGateway requireActiveGateway() {
        return activeGateway()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "PEACH_NOT_CONFIGURED",
                        "Online payments are not available right now. Choose Cash or Manual EFT."));
    }

    public OnlineGatewayPreference preference() {
        return platformSettingsRepository
                .findById(PlatformSettingsService.SINGLETON_ID)
                .map(PlatformSettings::getOnlinePaymentGateway)
                .orElse(OnlineGatewayPreference.AUTO);
    }

    public boolean isPayFastConfigured() {
        return payFastProperties.isConfigured();
    }

    public boolean isPeachConfigured() {
        return peachProperties.isConfigured();
    }

    /**
     * AUTO: PayFast when configured, else Peach when configured. An explicit choice is strict: when
     * that gateway is not configured there is no online option (never silently use the other account).
     */
    static Optional<PaymentGateway> resolve(
            OnlineGatewayPreference preference, boolean payFastConfigured, boolean peachConfigured) {
        OnlineGatewayPreference p = preference == null ? OnlineGatewayPreference.AUTO : preference;
        return switch (p) {
            case PAYFAST -> payFastConfigured ? Optional.of(PaymentGateway.PAYFAST) : Optional.empty();
            case PEACH -> peachConfigured ? Optional.of(PaymentGateway.PEACH) : Optional.empty();
            case AUTO -> payFastConfigured
                    ? Optional.of(PaymentGateway.PAYFAST)
                    : (peachConfigured ? Optional.of(PaymentGateway.PEACH) : Optional.empty());
        };
    }
}
