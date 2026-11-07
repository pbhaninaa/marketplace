package com.agrimarket.config;

import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails fast on UAT/PROD (and the aws/azure cloud profiles) when critical deployment variables are missing.
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
        try {
            validateDeployedConfig();
        } catch (IllegalStateException ex) {
            // Fail the process so Railway healthchecks surface a bad config instead of a half-started app.
            throw new IllegalStateException("Deployed profile validation failed: " + ex.getMessage(), ex);
        }
    }

    private void validateDeployedConfig() {
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
        // SecurityConfig drops localhost origins on UAT/PROD, so a localhost-only list would allow nothing.
        boolean hasNonLocalOrigin = java.util.Arrays.stream(cors.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> !s.toLowerCase(java.util.Locale.ROOT).matches("^https?://(localhost|127\\.0\\.0\\.1|\\[::1\\]).*"));
        if (!hasNonLocalOrigin) {
            throw new IllegalStateException("UAT_CORS_ORIGINS / PROD_CORS_ORIGINS must list your deployed frontend URL (not localhost)");
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

        if (peachProperties.isEnabled() && !peachProperties.isConfigured()) {
            throw new IllegalStateException(
                    "PEACH_ENABLED=true requires PEACH_CLIENT_ID, PEACH_CLIENT_SECRET, "
                            + "PEACH_MERCHANT_ID, PEACH_ENTITY_ID, and PEACH_SECRET_TOKEN");
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

    /** Deployed profiles: uat, prod, and the cloud profiles (aws, azure) that run with prod settings. */
    static final Set<String> DEPLOYED_PROFILES = Set.of("uat", "prod", "aws", "azure");

    private boolean isDeployedProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (DEPLOYED_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
