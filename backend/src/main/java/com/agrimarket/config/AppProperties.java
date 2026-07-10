package com.agrimarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String env,
        Cors cors,
        Jwt jwt,
        Cart cart,
        Otp otp,
        Payments payments,
        boolean seedDemoData) {

    /** Comma-separated frontend origins (single string — avoids indexed list override bugs). */
    public record Cors(String allowedOrigins) {}

    public record Jwt(String secret, long expirationMs) {}

    public record Cart(int sessionTtlHours) {}

    public record Otp(int ttlMinutes, int maxAttempts) {}

    public record Payments(String provider) {}
}
