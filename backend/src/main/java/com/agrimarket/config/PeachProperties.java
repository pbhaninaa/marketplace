package com.agrimarket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Platform Peach Payments merchant account configuration (Hosted Checkout V2).
 * There is a single platform-level Peach account — providers do not configure their own.
 */
@Component
public class PeachProperties {

    @Value("${peach.enabled:false}")
    private boolean enabled;

    @Value("${peach.sandbox:true}")
    private boolean sandbox;

    @Value("${peach.client-id:}")
    private String clientId;

    @Value("${peach.client-secret:}")
    private String clientSecret;

    @Value("${peach.merchant-id:}")
    private String merchantId;

    @Value("${peach.entity-id:}")
    private String entityId;

    @Value("${peach.secret-token:}")
    private String secretToken;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConfigured() {
        return enabled
                && !getClientId().isEmpty()
                && !getClientSecret().isEmpty()
                && !getMerchantId().isEmpty()
                && !getEntityId().isEmpty()
                && !getSecretToken().isEmpty();
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public String getClientId() {
        return clientId != null ? clientId.trim() : "";
    }

    public String getClientSecret() {
        return clientSecret != null ? clientSecret.trim() : "";
    }

    public String getMerchantId() {
        return merchantId != null ? merchantId.trim() : "";
    }

    public String getEntityId() {
        return entityId != null ? entityId.trim() : "";
    }

    public String getSecretToken() {
        return secretToken != null ? secretToken.trim() : "";
    }

    public String getAuthUrl() {
        return sandbox
                ? "https://sandbox-dashboard.peachpayments.com/api/oauth/token"
                : "https://dashboard.peachpayments.com/api/oauth/token";
    }

    public String getCheckoutUrl() {
        return sandbox
                ? "https://testsecure.peachpayments.com/v2/checkout"
                : "https://secure.peachpayments.com/v2/checkout";
    }
}
