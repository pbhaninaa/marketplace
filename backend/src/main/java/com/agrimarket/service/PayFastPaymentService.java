package com.agrimarket.service;

import com.agrimarket.api.dto.PayFastCheckoutResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.PasswordResetProperties;
import com.agrimarket.config.PayFastProperties;
import com.agrimarket.domain.PaymentGateway;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.PeachPaymentMethod;
import com.agrimarket.domain.SubscriptionActivationIntent;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.SubscriptionActivationIntentRepository;
import com.agrimarket.util.PayFastSignatureUtil;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PayFast hosted checkout (signed form POST from the browser) and ITN handling for the platform's
 * single PayFast merchant account. Covers buyer cart checkouts (orders + rentals of one seller) and
 * provider subscription quotes. Effects of a confirmed payment are shared with Peach through
 * {@link OnlinePaymentService}.
 */
@Service
@RequiredArgsConstructor
public class PayFastPaymentService {

    public static final String CUSTOM_ORDER = "ORDER";
    public static final String CUSTOM_SUB = "SUB";
    static final String STATUS_COMPLETE = "COMPLETE";
    static final String STATUS_CANCELLED = "CANCELLED";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PayFastPaymentService.class);

    private final PaymentRecordRepository paymentRecordRepository;
    private final SubscriptionActivationIntentRepository intentRepository;
    private final PayFastProperties properties;
    private final PasswordResetProperties passwordResetProperties;
    private final OnlinePaymentService onlinePaymentService;
    private final PayFastServerValidator serverValidator;

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * Signs a PayFast checkout covering every payment record of one cart checkout (one seller). One
     * {@code m_payment_id} is stored in gatewayMerchantRef on each record.
     */
    @Transactional
    public PayFastCheckoutResponse initiateCartCheckout(
            List<PaymentRecord> payments,
            PeachPaymentMethod method,
            String buyerName,
            String buyerEmail,
            String sellerName) {
        requireConfigured();
        if (payments == null || payments.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Nothing to pay for");
        }
        BigDecimal total = payments.stream().map(PaymentRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String ref = newPaymentId();
        for (PaymentRecord pr : payments) {
            pr.setGatewayMerchantRef(ref);
            pr.setGateway(PaymentGateway.PAYFAST);
            paymentRecordRepository.save(pr);
        }
        String returnUrl = frontendBaseUrl() + "/payment/return?ref=" + ref;
        Map<String, String> fields = signedFields(
                returnUrl,
                returnUrl + "&cancelled=1",
                buyerName,
                buyerEmail,
                ref,
                total,
                itemName(sellerName, "order"),
                CUSTOM_ORDER,
                requireMethod(method));
        return new PayFastCheckoutResponse(properties.getProcessUrl(), fields, ref);
    }

    /** Signs a PayFast checkout for a provider subscription activation quote. */
    @Transactional
    public PayFastCheckoutResponse initiateSubscriptionCheckout(
            Long providerId, Long intentId, PeachPaymentMethod method, String providerEmail) {
        requireConfigured();
        SubscriptionActivationIntent intent = intentRepository
                .findByIdAndProviderId(intentId, providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION", "Payment quote not found"));
        if (intent.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION", "This payment quote was already used");
        }
        PeachPaymentMethod selected = requireMethod(method);
        String ref = newPaymentId();
        intent.setGatewayMerchantRef(ref);
        intent.setGatewayCheckoutId(null);
        intent.setGatewayResultCode(null);
        intent.setGateway(PaymentGateway.PAYFAST);
        intentRepository.save(intent);

        String returnUrl = frontendBaseUrl() + "/provider/subscription?payRef=" + ref;
        String planName = intent.getPlan() == null ? "" : intent.getPlan().name().charAt(0)
                + intent.getPlan().name().substring(1).toLowerCase(java.util.Locale.ROOT);
        Map<String, String> fields = signedFields(
                returnUrl,
                returnUrl + "&cancelled=1",
                intent.getProvider() == null ? null : intent.getProvider().getName(),
                providerEmail,
                ref,
                intent.getAmountDue(),
                itemName("Marketplace " + planName, "subscription"),
                CUSTOM_SUB,
                selected);
        return new PayFastCheckoutResponse(properties.getProcessUrl(), fields, ref);
    }

    /**
     * Handles a PayFast ITN. Checks, in order: signature over all posted fields (blanks included),
     * merchant_id, the payment by m_payment_id (row locked), amount_gross to the cent, then (when
     * enabled) PayFast's own validate endpoint. Then COMPLETE marks paid, CANCELLED releases unpaid
     * rentals, anything else leaves the payment pending. Repeat ITNs change nothing.
     *
     * @param posted the posted fields in the order received
     * @throws ApiException 4xx when the ITN is rejected, 503 when PayFast could not be reached
     */
    @Transactional
    public void handleItn(Map<String, String> posted) {
        if (posted == null || posted.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Empty PayFast notification");
        }
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYFAST_NOT_CONFIGURED", "PayFast is not configured");
        }
        Map<String, String> params = Collections.unmodifiableMap(new LinkedHashMap<>(posted));

        String received = params.get("signature");
        if (received == null || received.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Missing PayFast signature");
        }
        String expected = PayFastSignatureUtil.itnSignature(params, properties.getPassphrase());
        if (!PayFastSignatureUtil.signaturesMatch(expected, received)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Invalid PayFast signature");
        }

        String merchantId = trimOrNull(params.get("merchant_id"));
        if (merchantId == null || !merchantId.equals(properties.getMerchantId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Unexpected PayFast merchant");
        }

        String ref = trimOrNull(params.get("m_payment_id"));
        if (ref == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Missing m_payment_id");
        }
        String kind = trimOrNull(params.get("custom_str1"));

        List<PaymentRecord> payments = paymentRecordRepository.findAllByGatewayMerchantRefForUpdate(ref);
        if (!payments.isEmpty()) {
            if (payments.stream().anyMatch(p -> p.getGateway() != PaymentGateway.PAYFAST)
                    || (kind != null && !CUSTOM_ORDER.equals(kind))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Reference is not a PayFast order payment");
            }
            BigDecimal total = payments.stream().map(PaymentRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            requireAmount(params.get("amount_gross"), total);
            requireServerValidation(params);
            handleOrderItn(payments, params);
            return;
        }

        Optional<SubscriptionActivationIntent> intentOpt = intentRepository.findByGatewayMerchantRefForUpdate(ref);
        if (intentOpt.isPresent()) {
            SubscriptionActivationIntent intent = intentOpt.get();
            if (intent.getGateway() != PaymentGateway.PAYFAST || (kind != null && !CUSTOM_SUB.equals(kind))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Reference is not a PayFast subscription payment");
            }
            requireAmount(params.get("amount_gross"), intent.getAmountDue());
            requireServerValidation(params);
            handleSubscriptionItn(intent, params);
            return;
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "PAYFAST", "Payment not found for this reference");
    }

    private void handleOrderItn(List<PaymentRecord> payments, Map<String, String> params) {
        String status = paymentStatus(params);
        boolean allPaid = payments.stream().allMatch(p -> p.getStatus() == PaymentRecordStatus.PAID);
        if (allPaid) {
            return; // repeat ITN after payment: nothing changes
        }
        for (PaymentRecord payment : payments) {
            payment.setGatewayResultCode(truncate(status, 32));
            paymentRecordRepository.save(payment);
        }
        if (STATUS_COMPLETE.equals(status)) {
            onlinePaymentService.confirmCheckoutPaid(payments, trimOrNull(params.get("pf_payment_id")));
        } else if (STATUS_CANCELLED.equals(status)) {
            onlinePaymentService.releaseUnpaidRentals(payments);
        }
    }

    private void handleSubscriptionItn(SubscriptionActivationIntent intent, Map<String, String> params) {
        if (intent.isUsed()) {
            return;
        }
        String status = paymentStatus(params);
        intent.setGatewayResultCode(truncate(status, 32));
        intentRepository.save(intent);
        if (STATUS_COMPLETE.equals(status)) {
            onlinePaymentService.activateSubscription(intent, trimOrNull(params.get("pf_payment_id")), "PayFast");
        }
    }

    private void requireServerValidation(Map<String, String> params) {
        if (!properties.isValidateWithServer()) {
            return;
        }
        boolean valid;
        try {
            valid = serverValidator.isValid(PayFastSignatureUtil.itnParamString(params));
        } catch (IOException e) {
            log.warn("PayFast ITN server validation failed to run: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYFAST", "Could not validate the notification with PayFast");
        }
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "PayFast did not confirm this notification");
        }
    }

    private static void requireAmount(String raw, BigDecimal expected) {
        String value = trimOrNull(raw);
        if (value == null || expected == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Missing amount_gross");
        }
        BigDecimal paid;
        try {
            paid = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "Invalid amount_gross");
        }
        if (paid.compareTo(expected.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYFAST", "PayFast amount does not match the amount due");
        }
    }

    /**
     * Checkout fields in PayFast's documented order, blanks dropped, signature last.
     * Visible for tests.
     */
    Map<String, String> signedFields(
            String returnUrl,
            String cancelUrl,
            String buyerName,
            String buyerEmail,
            String paymentId,
            BigDecimal amount,
            String itemName,
            String customStr1,
            PeachPaymentMethod method) {
        String[] names = splitName(buyerName);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchant_id", properties.getMerchantId());
        fields.put("merchant_key", properties.getMerchantKey());
        fields.put("return_url", returnUrl);
        fields.put("cancel_url", cancelUrl);
        fields.put("notify_url", properties.getNotifyUrl());
        fields.put("name_first", truncate(names[0], 100));
        fields.put("name_last", truncate(names[1], 100));
        fields.put("email_address", truncate(trimOrNull(buyerEmail), 100));
        fields.put("m_payment_id", paymentId);
        fields.put("amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        fields.put("item_name", truncate(itemName, 100));
        fields.put("custom_str1", customStr1);
        fields.put("payment_method", payFastMethod(method));
        fields.values().removeIf(v -> v == null || v.trim().isEmpty());
        fields.replaceAll((k, v) -> v.trim());
        fields.put("signature", PayFastSignatureUtil.checkoutSignature(fields, properties.getPassphrase()));
        return fields;
    }

    /** Card maps to PayFast {@code cc}, Instant EFT to {@code eft}. */
    static String payFastMethod(PeachPaymentMethod method) {
        return method == PeachPaymentMethod.EFT ? "eft" : "cc";
    }

    private static PeachPaymentMethod requireMethod(PeachPaymentMethod method) {
        if (method == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PEACH_PAYMENT_METHOD", "Choose Card or Instant EFT.");
        }
        return method;
    }

    private static String itemName(String name, String suffix) {
        String base = trimOrNull(name);
        String out = base == null ? suffix : base + " " + suffix;
        return Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    private static String[] splitName(String fullName) {
        String n = trimOrNull(fullName);
        if (n == null) {
            return new String[] {null, null};
        }
        n = n.replaceAll("\\s+", " ");
        int space = n.indexOf(' ');
        return space < 0 ? new String[] {n, null} : new String[] {n.substring(0, space), n.substring(space + 1)};
    }

    private static String paymentStatus(Map<String, String> params) {
        String s = trimOrNull(params.get("payment_status"));
        return s == null ? "" : s.toUpperCase(java.util.Locale.ROOT);
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "PEACH_NOT_CONFIGURED", "Online payments are not available right now.");
        }
    }

    private String frontendBaseUrl() {
        return PeachPaymentService.frontendBaseUrl(passwordResetProperties);
    }

    /** 32 hex characters: unguessable, fits gateway_merchant_ref and PayFast's 100-character limit. */
    private static String newPaymentId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
