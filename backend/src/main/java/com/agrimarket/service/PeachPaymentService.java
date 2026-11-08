package com.agrimarket.service;

import com.agrimarket.api.dto.PeachCheckoutResponse;
import com.agrimarket.api.dto.PeachStatusResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.PasswordResetProperties;
import com.agrimarket.config.PeachProperties;
import com.agrimarket.domain.PaymentGateway;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.PeachPaymentMethod;
import com.agrimarket.domain.SubscriptionActivationIntent;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.SubscriptionActivationIntentRepository;
import com.agrimarket.util.PeachSignatureUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Peach Payments Hosted Checkout V2 integration for the platform's single Peach merchant account.
 * Handles OAuth2 client-credentials auth, checkout creation for guest cart orders/rentals and for
 * provider subscription quotes, and webhook / shopperResultUrl notifications.
 */
@Service
@RequiredArgsConstructor
public class PeachPaymentService {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    /** Refresh a little before the token's reported expiry to avoid races with in-flight requests. */
    private static final long TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS = 30;

    private final PaymentRecordRepository paymentRecordRepository;
    private final SubscriptionActivationIntentRepository intentRepository;
    private final PeachProperties peachProperties;
    private final PasswordResetProperties passwordResetProperties;
    private final OnlinePaymentService onlinePaymentService;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedAccessToken;
    private volatile Instant cachedAccessTokenExpiry = Instant.EPOCH;

    public boolean isConfigured() {
        return peachProperties.isConfigured();
    }

    /**
     * Creates a single Peach Hosted Checkout session covering every payment record created in one
     * guest cart checkout (a sale order and/or one or more rental bookings from the same session).
     */
    @Transactional
    public PeachCheckoutResponse initiateCartCheckout(
            List<PaymentRecord> payments, PeachPaymentMethod peachPaymentMethod) {
        requireConfigured();
        if (payments == null || payments.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Nothing to pay for");
        }

        BigDecimal total = payments.stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String merchantRef = newMerchantTransactionId();
        String amountStr = String.format(Locale.US, "%.2f", total);
        String shopperResultUrl = callbackUrl("/api/public/peach/return");

        JsonNode response = createCheckout(
                buildCheckoutFields(merchantRef, amountStr, shopperResultUrl, requirePeachMethod(peachPaymentMethod)));
        String checkoutId = requireField(response, "checkoutId");
        String redirectUrl = requireField(response, "redirectUrl");

        for (PaymentRecord pr : payments) {
            pr.setGatewayMerchantRef(merchantRef);
            pr.setGatewayCheckoutId(checkoutId);
            pr.setGateway(PaymentGateway.PEACH);
            paymentRecordRepository.save(pr);
        }

        return new PeachCheckoutResponse(redirectUrl, checkoutId, merchantRef);
    }

    /** Creates a Peach Hosted Checkout session for a provider subscription activation quote. */
    @Transactional
    public PeachCheckoutResponse initiateSubscriptionCheckout(
            Long providerId, Long intentId, PeachPaymentMethod peachPaymentMethod) {
        requireConfigured();
        SubscriptionActivationIntent intent = intentRepository
                .findByIdAndProviderId(intentId, providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION", "Payment quote not found"));
        if (intent.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION", "This payment quote was already used");
        }

        String merchantRef = newMerchantTransactionId();
        String amountStr = String.format(Locale.US, "%.2f", intent.getAmountDue());
        String shopperResultUrl = callbackUrl("/api/public/peach/return");
        PeachPaymentMethod selectedMethod = requirePeachMethod(peachPaymentMethod);
        JsonNode response = createCheckout(
                buildCheckoutFields(merchantRef, amountStr, shopperResultUrl, selectedMethod));
        String checkoutId = requireField(response, "checkoutId");
        String redirectUrl = requireField(response, "redirectUrl");

        intent.setGatewayMerchantRef(merchantRef);
        intent.setGatewayCheckoutId(checkoutId);
        intent.setGateway(PaymentGateway.PEACH);
        intentRepository.save(intent);

        return new PeachCheckoutResponse(redirectUrl, checkoutId, merchantRef);
    }

    /**
     * Peach webhook / shopperResultUrl handler. Idempotent — safe to call more than once for the
     * same reference (e.g. once from the redirect and once from the async webhook).
     */
    @Transactional
    public void handleWebhook(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Empty Peach notification");
        }

        requireConfigured();
        String secretToken = peachProperties.getSecretToken();
        String receivedSignature = params.get("signature");
        if (receivedSignature == null || receivedSignature.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Missing Peach signature");
        }
        String expected = PeachSignatureUtil.buildSignature(params, secretToken);
        if (!PeachSignatureUtil.signaturesMatch(expected, receivedSignature)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Invalid Peach signature");
        }

        String ref = trimOrNull(params.get("merchantTransactionId"));
        if (ref == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Missing merchantTransactionId");
        }

        validateCommonNotification(params);

        List<PaymentRecord> payments = paymentRecordRepository.findAllByGatewayMerchantRefForUpdate(ref);
        if (!payments.isEmpty()) {
            handleOrderWebhook(payments, params);
            return;
        }

        Optional<SubscriptionActivationIntent> intentOpt = intentRepository.findByGatewayMerchantRefForUpdate(ref);
        if (intentOpt.isPresent()) {
            handleSubscriptionWebhook(intentOpt.get(), params);
            return;
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "PEACH", "Payment not found for this reference");
    }

    /** Guest-facing status lookup for the return page (keyed by the unguessable merchant reference). */
    @Transactional(readOnly = true)
    public PeachStatusResponse getStatusByRef(String ref) {
        return onlinePaymentService.getStatusByRef(ref);
    }

    /** Builds the browser destination after Peach POSTs its signed result to the backend. */
    @Transactional(readOnly = true)
    public String getFrontendReturnUrl(String ref) {
        String cleaned = trimOrNull(ref);
        if (cleaned == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Missing reference");
        }
        if (!paymentRecordRepository.findAllByGatewayMerchantRef(cleaned).isEmpty()) {
            return UriComponentsBuilder.fromUriString(frontendBaseUrl())
                    .path("/payment/return")
                    .queryParam("ref", cleaned)
                    .build()
                    .encode()
                    .toUriString();
        }
        if (intentRepository.findByGatewayMerchantRef(cleaned).isPresent()) {
            return UriComponentsBuilder.fromUriString(frontendBaseUrl())
                    .path("/provider/subscription")
                    .queryParam("peachRef", cleaned)
                    .build()
                    .encode()
                    .toUriString();
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "PEACH", "Payment reference not found");
    }

    private void handleOrderWebhook(List<PaymentRecord> payments, Map<String, String> params) {
        String resultCode = resultCode(params);
        for (PaymentRecord payment : payments) {
            payment.setGatewayResultCode(resultCode);
            paymentRecordRepository.save(payment);
        }
        boolean allPaid = payments.stream().allMatch(p -> p.getStatus() == PaymentRecordStatus.PAID);
        if (allPaid) {
            return;
        }
        if (isCancelledResultCode(resultCode)) {
            // The buyer cancelled on the Peach page: release the rental slots this checkout was holding,
            // so other buyers can book those times. (Cash/EFT bookings are untouched.)
            onlinePaymentService.releaseUnpaidRentals(payments);
            return;
        }
        if (!isSuccessfulNotification(params)) {
            return;
        }

        BigDecimal total = payments.stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        validateAmount(params.get("amount"), total, "order total");
        validateCheckoutId(params.get("checkoutId"), payments.get(0).getGatewayCheckoutId());

        onlinePaymentService.confirmCheckoutPaid(payments, trimOrNull(params.get("checkoutId")));
    }

    private void handleSubscriptionWebhook(SubscriptionActivationIntent intent, Map<String, String> params) {
        intent.setGatewayResultCode(resultCode(params));
        intentRepository.save(intent);
        if (intent.isUsed()) {
            return;
        }
        if (!isSuccessfulNotification(params)) {
            return;
        }
        validateAmount(params.get("amount"), intent.getAmountDue(), "subscription quote");
        validateCheckoutId(params.get("checkoutId"), intent.getGatewayCheckoutId());

        onlinePaymentService.activateSubscription(intent, trimOrNull(params.get("checkoutId")), "Peach");
    }

    private void validateAmount(String rawAmount, BigDecimal expected, String label) {
        if (rawAmount == null || rawAmount.isBlank() || expected == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Missing amount from Peach");
        }
        try {
            BigDecimal paid = new BigDecimal(rawAmount.trim());
            if (paid.compareTo(expected) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Peach amount does not match " + label);
            }
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Invalid amount from Peach");
        }
    }

    private void validateCommonNotification(Map<String, String> params) {
        String currency = trimOrNull(params.get("currency"));
        if (!"ZAR".equals(currency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Unexpected Peach currency");
        }
        String entityId = trimOrNull(params.get("authentication.entityId"));
        if (entityId == null || !entityId.equals(peachProperties.getEntityId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Unexpected Peach entity");
        }
        String paymentType = trimOrNull(params.get("paymentType"));
        if (!"DB".equals(paymentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Unexpected Peach payment type");
        }
        if (trimOrNull(params.get("checkoutId")) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Missing Peach checkoutId");
        }
    }

    private void validateCheckoutId(String actual, String expected) {
        String received = trimOrNull(actual);
        String stored = trimOrNull(expected);
        if (received == null || stored == null || !stored.equals(received)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH", "Peach checkoutId does not match");
        }
    }

    private static boolean isSuccessfulNotification(Map<String, String> params) {
        String status = params.get("status");
        if (status != null && "successful".equalsIgnoreCase(status.trim())) {
            return true;
        }
        String resultCode = resultCode(params);
        if (resultCode == null || resultCode.isBlank()) {
            return false;
        }
        String code = resultCode.trim();
        return code.startsWith("000.000") || code.startsWith("000.100");
    }

    private static String resultCode(Map<String, String> params) {
        String resultCode = params.get("result.code");
        if (resultCode == null || resultCode.isBlank()) {
            resultCode = params.get("result_code");
        }
        return trimOrNull(resultCode);
    }

    private static boolean isCancelledResultCode(String code) {
        return code != null && code.startsWith("100.396.101");
    }

    /** Frontend base URL (public app base URL), without a trailing slash. Shared with PayFast. */
    static String frontendBaseUrl(PasswordResetProperties props) {
        String base = props.getPublicAppBaseUrl();
        if (base == null || base.isBlank()) {
            return "http://localhost:5173";
        }
        base = base.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private Map<String, String> buildCheckoutFields(
            String merchantRef,
            String amountStr,
            String shopperResultUrl,
            PeachPaymentMethod peachPaymentMethod) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("authentication.entityId", peachProperties.getEntityId());
        fields.put("merchantTransactionId", merchantRef);
        fields.put("amount", amountStr);
        fields.put("currency", "ZAR");
        fields.put("paymentType", "DB");
        applyHostedPaymentMethod(fields, peachPaymentMethod);
        fields.put("nonce", UUID.randomUUID().toString().replace("-", ""));
        fields.put("shopperResultUrl", shopperResultUrl);
        String notificationUrl = notificationUrl();
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            fields.put("notificationUrl", notificationUrl);
        }
        return fields;
    }

    static void applyHostedPaymentMethod(
            Map<String, String> fields, PeachPaymentMethod peachPaymentMethod) {
        fields.put("defaultPaymentMethod", peachPaymentMethod.hostedCheckoutValue());
        fields.put("forceDefaultMethod", "true");
    }

    private static PeachPaymentMethod requirePeachMethod(PeachPaymentMethod method) {
        if (method == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PEACH_PAYMENT_METHOD", "Choose Card or Instant EFT.");
        }
        return method;
    }

    private String notificationUrl() {
        return callbackUrl("/api/public/peach/webhook");
    }

    private String callbackUrl(String path) {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).build().toUriString();
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PEACH_CALLBACK_URL",
                    "Unable to determine the public payment callback URL.");
        }
    }

    private String frontendBaseUrl() {
        String base = passwordResetProperties.getPublicAppBaseUrl();
        if (base == null || base.isBlank()) {
            return "http://localhost:5173";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private void requireConfigured() {
        if (!peachProperties.isConfigured()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PEACH_NOT_CONFIGURED", "Online payments are not available right now.");
        }
    }

    private static String newMerchantTransactionId() {
        // Peach requires merchantTransactionId length 8-16.
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String requireField(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PEACH", "Peach checkout did not return a " + field);
        }
        return value;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }

    private JsonNode createCheckout(Map<String, String> fields) {
        try {
            String accessToken = fetchAccessToken();
            String formBody = fields.entrySet().stream()
                    .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining("&"));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(peachProperties.getCheckoutUrl()))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Referer", frontendBaseUrl())
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = textOrNull(node, "description");
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "PEACH",
                        "Peach checkout request failed" + (message != null ? ": " + message : ""));
            }
            return node;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PEACH", "Unable to reach Peach checkout service");
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private synchronized String fetchAccessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(cachedAccessTokenExpiry)) {
            return cachedAccessToken;
        }
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("clientId", peachProperties.getClientId());
            body.put("clientSecret", peachProperties.getClientSecret());
            body.put("merchantId", peachProperties.getMerchantId());
            String json = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(peachProperties.getAuthUrl()))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PEACH", "Unable to authenticate with Peach Payments");
            }
            String accessToken = textOrNull(node, "access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY, "PEACH", "Peach authentication response missing access_token");
            }
            long expiresInSeconds = node.hasNonNull("expires_in") ? node.get("expires_in").asLong(3600) : 3600;
            long safeSeconds = Math.max(expiresInSeconds - TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS, 5);

            cachedAccessToken = accessToken;
            cachedAccessTokenExpiry = now.plusSeconds(safeSeconds);
            return accessToken;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PEACH", "Unable to reach Peach authentication service");
        }
    }
}
