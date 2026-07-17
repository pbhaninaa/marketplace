package com.agrimarket.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails fast on UAT/PROD when critical deployment variables are missing.
 */
@Component
public class DeployedProfileValidator {

    private final Environment environment;
    private final EmailProperties emailProperties;
    private final PeachProperties peachProperties;

    public DeployedProfileValidator(
            Environment environment, EmailProperties emailProperties, PeachProperties peachProperties) {
        this.environment = environment;
        this.emailProperties = emailProperties;
        this.peachProperties = peachProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (!isDeployedProfile()) {
            return;
        }
        String jwt = environment.getProperty("app.jwt.secret", "").trim();
        if (jwt.length() < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least 32 characters for UAT/PROD");
        }
        if (jwt.contains("local-dev-jwt") || jwt.contains("sit-jwt")) {
            throw new IllegalStateException("APP_JWT_SECRET must not use a development default on UAT/PROD");
        }

        String cors = firstNonBlank(
                environment.getProperty("PROD_CORS_ORIGINS"),
                environment.getProperty("UAT_CORS_ORIGINS"),
                environment.getProperty("app.cors.allowed-origins"));
        if (cors == null || cors.isBlank()) {
            throw new IllegalStateException("Set UAT_CORS_ORIGINS or PROD_CORS_ORIGINS to your frontend URL");
        }

        String publicUrl = firstNonBlank(
                environment.getProperty("PUBLIC_APP_BASE_URL"),
                environment.getProperty("app.password-reset.public-app-base-url"));
        if (publicUrl == null || publicUrl.isBlank() || publicUrl.contains("localhost")) {
            throw new IllegalStateException("Set PUBLIC_APP_BASE_URL to your public frontend URL (no localhost)");
        }

        if (emailProperties.isEnabled()) {
            String apiKey = emailProperties.getSendgridApiKey() == null
                    ? ""
                    : emailProperties.getSendgridApiKey().trim();
            if (apiKey.isEmpty() || !emailProperties.hasConfiguredSender()) {
                throw new IllegalStateException(
                        "Email is enabled: set SENDGRID_API_KEY and either EMAIL_DOMAIN or EMAIL_FROM");
            }
            if (!emailProperties.hasValidConfiguredDomain()) {
                throw new IllegalStateException(
                        "EMAIL_DOMAIN must be a domain such as example.com (without a scheme, path, or @)");
            }
        }

        if (peachProperties.isEnabled() && !peachProperties.isConfigured()) {
            throw new IllegalStateException(
                    "PEACH_ENABLED=true requires PEACH_CLIENT_ID, PEACH_CLIENT_SECRET, "
                            + "PEACH_MERCHANT_ID, PEACH_ENTITY_ID, and PEACH_SECRET_TOKEN");
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private boolean isDeployedProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("uat".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
