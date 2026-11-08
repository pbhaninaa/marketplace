package com.agrimarket.service;

import com.agrimarket.config.PayFastProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Posts an ITN's parameter string back to PayFast's validate endpoint ({@code /eng/query/validate})
 * and reports whether PayFast answered {@code VALID}. A separate bean so tests can replace it.
 */
@Component
public class PayFastServerValidator {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final PayFastProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public PayFastServerValidator(PayFastProperties properties) {
        this.properties = properties;
    }

    /**
     * @return true when PayFast confirms the ITN data
     * @throws IOException when PayFast could not be reached (the ITN should be retried)
     */
    public boolean isValid(String itnParamString) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getValidateUrl()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(itnParamString == null ? "" : itnParamString))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("PayFast validate returned HTTP " + response.statusCode());
            }
            return "VALID".equals(response.body() == null ? "" : response.body().trim());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while validating with PayFast", e);
        }
    }
}
