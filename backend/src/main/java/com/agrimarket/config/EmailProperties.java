package com.agrimarket.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.email")
@Getter
@Setter
public class EmailProperties {
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
     * Public frontend base URL used in links (e.g. https://app.example.com).
     */
    private String publicAppBaseUrl = "http://localhost:5173";
}

