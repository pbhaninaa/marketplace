package com.agrimarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.password-reset")
public class PasswordResetProperties {

    /** Hours until reset link expires. */
    private int tokenTtlHours = 24;

    /** Public UI base URL (no trailing slash) for reset links in logs. */
    private String publicAppBaseUrl = "http://localhost:5173";

    public int getTokenTtlHours() {
        return tokenTtlHours;
    }

    public void setTokenTtlHours(int tokenTtlHours) {
        this.tokenTtlHours = tokenTtlHours;
    }

    public String getPublicAppBaseUrl() {
        return publicAppBaseUrl;
    }

    public void setPublicAppBaseUrl(String publicAppBaseUrl) {
        this.publicAppBaseUrl = publicAppBaseUrl;
    }
}
