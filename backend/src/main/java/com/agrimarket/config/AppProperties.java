package com.agrimarket.config;

import java.util.List;
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

    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(String secret, long expirationMs) {}

    public record Cart(int sessionTtlHours) {}

    public record Otp(int ttlMinutes, int maxAttempts) {}

    public record Payments(String provider) {}
}
