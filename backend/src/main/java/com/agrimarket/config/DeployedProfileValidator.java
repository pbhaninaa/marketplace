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

    public DeployedProfileValidator(Environment environment, EmailProperties emailProperties) {
        this.environment = environment;
        this.emailProperties = emailProperties;
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

        String cors = environment.getProperty("app.cors.allowed-origins", "").trim();
        if (cors.isEmpty()) {
            throw new IllegalStateException("Set UAT_CORS_ORIGINS or PROD_CORS_ORIGINS to your frontend URL(s)");
        }

        String publicUrl = environment.getProperty("app.password-reset.public-app-base-url", "").trim();
        if (publicUrl.isEmpty() || publicUrl.contains("localhost")) {
            throw new IllegalStateException("Set PUBLIC_APP_BASE_URL to your public frontend URL (no localhost)");
        }

        if (emailProperties.isEnabled()) {
            String apiKey = emailProperties.getSendgridApiKey() == null
                    ? ""
                    : emailProperties.getSendgridApiKey().trim();
            String from = emailProperties.getFrom() == null ? "" : emailProperties.getFrom().trim();
            if (apiKey.isEmpty() || from.isEmpty()) {
                throw new IllegalStateException("Email is enabled: set SENDGRID_API_KEY and EMAIL_FROM");
            }
        }
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
