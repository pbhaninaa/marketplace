package com.agrimarket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Platform PayFast merchant account (hosted checkout by form POST + ITN notifications).
 * There is a single platform-level account: providers do not configure their own.
 */
@Component
public class PayFastProperties {

    public static final String SANDBOX_HOST = "https://sandbox.payfast.co.za";
    public static final String LIVE_HOST = "https://www.payfast.co.za";
    public static final String PROCESS_PATH = "/eng/process";
    public static final String VALIDATE_PATH = "/eng/query/validate";
    /** Backend path PayFast posts ITNs to (appended to {@code payfast.public-base-url}). */
    public static final String NOTIFY_PATH = "/api/public/payfast/notify";

    @Value("${payfast.enabled:false}")
    private boolean enabled;

    @Value("${payfast.sandbox:true}")
    private boolean sandbox = true;

    @Value("${payfast.merchant-id:}")
    private String merchantId;

    @Value("${payfast.merchant-key:}")
    private String merchantKey;

    @Value("${payfast.passphrase:}")
    private String passphrase;

    /** Public https base URL of this backend, used to build notify_url. */
    @Value("${payfast.public-base-url:}")
    private String publicBaseUrl;

    /** POST every ITN back to PayFast's validate endpoint and require "VALID". */
    @Value("${payfast.validate-with-server:true}")
    private boolean validateWithServer = true;

    public boolean isEnabled() {
        return enabled;
    }

    /** Enough to take payments: merchant id/key and a public base URL for notify_url. */
    public boolean isConfigured() {
        return enabled && !getMerchantId().isEmpty() && !getMerchantKey().isEmpty() && !getPublicBaseUrl().isEmpty();
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public boolean isValidateWithServer() {
        return validateWithServer;
    }

    public String getMerchantId() {
        return merchantId != null ? merchantId.trim() : "";
    }

    public String getMerchantKey() {
        return merchantKey != null ? merchantKey.trim() : "";
    }

    public String getPassphrase() {
        return passphrase != null ? passphrase.trim() : "";
    }

    public String getPublicBaseUrl() {
        String base = publicBaseUrl != null ? publicBaseUrl.trim() : "";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    public String getHost() {
        return sandbox ? SANDBOX_HOST : LIVE_HOST;
    }

    public String getProcessUrl() {
        return getHost() + PROCESS_PATH;
    }

    public String getValidateUrl() {
        return getHost() + VALIDATE_PATH;
    }

    public String getNotifyUrl() {
        return getPublicBaseUrl() + NOTIFY_PATH;
    }

    // Setters for unit tests that build the properties by hand.
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public void setMerchantKey(String merchantKey) {
        this.merchantKey = merchantKey;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public void setValidateWithServer(boolean validateWithServer) {
        this.validateWithServer = validateWithServer;
    }
}
