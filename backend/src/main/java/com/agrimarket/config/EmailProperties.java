package com.agrimarket.config;

import com.agrimarket.service.EmailPurpose;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.email")
@Getter
@Setter
public class EmailProperties {
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$");

    /**
     * Master switch. Keep false in local until SMTP/API key is configured.
     */
    private boolean enabled = false;

    /**
     * SendGrid API key (recommended to supply via env var).
     */
    private String sendgridApiKey = "";

    /**
     * Verified sender in SendGrid (e.g. no-reply@yourdomain.com).
     */
    private String from = "";

    /**
     * Verified SendGrid domain used to build purpose addresses such as
     * security@example.com. EMAIL_FROM remains the fallback when this is unset.
     */
    private String domain = "";

    /**
     * Public frontend base URL used in links (e.g. https://app.example.com).
     */
    private String publicAppBaseUrl = "http://localhost:5173";

    public String resolveFrom(EmailPurpose purpose) {
        String configuredDomain = normalizedDomain();
        if (!configuredDomain.isEmpty()) {
            EmailPurpose resolvedPurpose = purpose == null ? EmailPurpose.NO_REPLY : purpose;
            return resolvedPurpose.localPart() + "@" + configuredDomain;
        }
        return from == null ? "" : from.trim();
    }

    public boolean hasConfiguredSender() {
        return !normalizedDomain().isEmpty() || (from != null && !from.isBlank());
    }

    public boolean hasValidConfiguredDomain() {
        String configuredDomain = normalizedDomain();
        return configuredDomain.isEmpty() || DOMAIN_PATTERN.matcher(configuredDomain).matches();
    }

    private String normalizedDomain() {
        return domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
    }
}

