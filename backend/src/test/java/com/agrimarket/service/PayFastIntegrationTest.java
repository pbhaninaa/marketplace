package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.PayFastProperties;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentGateway;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.PeachPaymentMethod;
import com.agrimarket.domain.PlatformSettings;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.SettlementStatus;
import com.agrimarket.domain.SubscriptionActivationIntent;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.PlatformSettingsRepository;
import com.agrimarket.repo.SubscriptionActivationIntentRepository;
import com.agrimarket.repo.SubscriptionRepository;
import com.agrimarket.support.TestFixtures;
import com.agrimarket.util.PayFastSignatureUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TestPropertySource(properties = {
    "payfast.enabled=true",
    "payfast.sandbox=true",
    "payfast.merchant-id=10000100",
    "payfast.merchant-key=46f0cd694581a",
    "payfast.passphrase=jt7NOE43FZPn",
    "payfast.public-base-url=https://api.test.example/",
    "payfast.validate-with-server=false"
})
class PayFastIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSPHRASE = "jt7NOE43FZPn";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private PayFastPaymentService payFastPaymentService;
    @Autowired private PayFastProperties payFastProperties;
    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private PlatformSettingsRepository platformSettingsRepository;
    @Autowired private PlatformSettingsService platformSettingsService;
    @Autowired private SubscriptionActivationIntentRepository intentRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @MockBean private PayFastServerValidator serverValidator;

    private Provider provider;
    private Listing hay;
    private Listing tractor;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Category cat = fixtures.saveCategory("Farm", "farm-" + u);
        provider = fixtures.saveActiveProvider("Green Acres", "green-" + u);
        fixtures.saveActiveSubscription(provider);
        hay = fixtures.saveSaleListing(provider, cat, "Bale of hay", new BigDecimal("50"), 20);
        tractor = fixtures.saveRentListing(provider, cat, "Tractor", new BigDecimal("500"));

        PlatformSettings settings = platformSettingsService.getOrCreate();
        settings.setOnlineOrderFeePercent(new BigDecimal("7.50"));
        platformSettingsRepository.save(settings);
    }

    // ---- Checkout ----

    @Test
    void onlineCheckoutReturnsSignedPayFastFormInDocumentedOrder() throws Exception {
        JsonNode res = checkoutSaleOrder("CARD");

        assertThat(res.path("gateway").asText()).isEqualTo("PAYFAST");
        assertThat(res.has("redirectUrl")).isFalse();
        assertThat(res.at("/payfast/processUrl").asText()).isEqualTo("https://sandbox.payfast.co.za/eng/process");

        Map<String, String> fields = fields(res);
        assertThat(new ArrayList<>(fields.keySet())).containsExactly(
                "merchant_id", "merchant_key", "return_url", "cancel_url", "notify_url",
                "name_first", "name_last", "email_address", "m_payment_id", "amount",
                "item_name", "custom_str1", "payment_method", "signature");
        String ref = res.path("paymentRef").asText();
        assertThat(fields.get("m_payment_id")).isEqualTo(ref);
        assertThat(fields.get("merchant_id")).isEqualTo("10000100");
        assertThat(fields.get("amount")).isEqualTo("100.00");
        assertThat(fields.get("name_first")).isEqualTo("Thandi");
        assertThat(fields.get("name_last")).isEqualTo("Mokoena");
        assertThat(fields.get("item_name")).isEqualTo("Green Acres order");
        assertThat(fields.get("custom_str1")).isEqualTo("ORDER");
        assertThat(fields.get("payment_method")).isEqualTo("cc");
        assertThat(fields.get("notify_url")).isEqualTo("https://api.test.example/api/public/payfast/notify");
        assertThat(fields.get("return_url")).endsWith("/payment/return?ref=" + ref);
        assertThat(fields.get("cancel_url")).endsWith("/payment/return?ref=" + ref + "&cancelled=1");

        Map<String, String> unsigned = new LinkedHashMap<>(fields);
        String signature = unsigned.remove("signature");
        assertThat(signature).isEqualTo(PayFastSignatureUtil.checkoutSignature(unsigned, PASSPHRASE));

        List<PaymentRecord> payments = paymentRecordRepository.findAllByGatewayMerchantRef(ref);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getGateway()).isEqualTo(PaymentGateway.PAYFAST);
        assertThat(payments.get(0).getSettlementStatus()).isEqualTo(SettlementStatus.NONE);

        assertThat(fields(checkoutSaleOrder("EFT")).get("payment_method")).isEqualTo("eft");
    }

    @Test
    void onlineStatusReportsPayFastWithoutSecrets() throws Exception {
        String body = mockMvc.perform(get("/api/public/payments/online-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.gateway").value("PAYFAST"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("46f0cd694581a").doesNotContain(PASSPHRASE);
    }

    // ---- ITN ----

    @Test
    void completeItnMarksPaidOnceAndRecordsSettlement() throws Exception {
        String ref = checkoutSaleOrder("CARD").path("paymentRef").asText();

        Map<String, String> itn = signed(itn(ref, "100.00", "COMPLETE", "ORDER"));
        payFastPaymentService.handleItn(itn);

        PaymentRecord pr = paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0);
        Order order = pr.getOrder();
        assertThat(pr.getStatus()).isEqualTo(PaymentRecordStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(pr.getGatewayCheckoutId()).isEqualTo("1089250");
        assertThat(pr.getSettlementStatus()).isEqualTo(SettlementStatus.HELD);
        assertThat(pr.getPlatformFeePercent()).isEqualByComparingTo("7.50");
        assertThat(pr.getPlatformFee()).isEqualByComparingTo("7.50");
        assertThat(pr.getSellerPayoutAmount()).isEqualByComparingTo("92.50");
        Instant paidAt = pr.getPaidAt();
        assertThat(paidAt).isNotNull();

        // A repeat ITN (PayFast retries) changes nothing.
        payFastPaymentService.handleItn(itn);
        PaymentRecord again = paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0);
        assertThat(again.getPaidAt()).isEqualTo(paidAt);
        assertThat(again.getSettlementStatus()).isEqualTo(SettlementStatus.HELD);
        assertThat(again.getSellerPayoutAmount()).isEqualByComparingTo("92.50");

        mockMvc.perform(get("/api/public/payments/status").param("ref", ref))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void badSignatureWrongMerchantAndWrongAmountAreRejected() throws Exception {
        String ref = checkoutSaleOrder("CARD").path("paymentRef").asText();

        Map<String, String> tampered = signed(itn(ref, "100.00", "COMPLETE", "ORDER"));
        tampered.put("amount_gross", "1.00"); // changed after signing
        assertRejected(tampered, "Invalid PayFast signature");

        Map<String, String> otherMerchant = itn(ref, "100.00", "COMPLETE", "ORDER");
        otherMerchant.put("merchant_id", "99999999");
        assertRejected(signed(otherMerchant), "Unexpected PayFast merchant");

        assertRejected(signed(itn(ref, "99.99", "COMPLETE", "ORDER")), "amount does not match");
        assertRejected(signed(itn(ref, "100.001", "COMPLETE", "ORDER")), "amount does not match");

        Map<String, String> missingSignature = itn(ref, "100.00", "COMPLETE", "ORDER");
        assertRejected(missingSignature, "Missing PayFast signature");

        PaymentRecord pr = paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0);
        assertThat(pr.getStatus()).isEqualTo(PaymentRecordStatus.PENDING_PAYMENT);
        assertThat(pr.getOrder().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(pr.getSettlementStatus()).isEqualTo(SettlementStatus.NONE);
    }

    @Test
    void serverValidationMustAnswerValid() throws Exception {
        String ref = checkoutSaleOrder("CARD").path("paymentRef").asText();
        Map<String, String> itn = signed(itn(ref, "100.00", "COMPLETE", "ORDER"));
        payFastProperties.setValidateWithServer(true);
        try {
            when(serverValidator.isValid(anyString())).thenReturn(false);
            assertRejected(itn, "did not confirm");
            verify(serverValidator).isValid(PayFastSignatureUtil.itnParamString(itn));

            when(serverValidator.isValid(anyString())).thenThrow(new java.io.IOException("timeout"));
            assertThatThrownBy(() -> payFastPaymentService.handleItn(itn))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(503));

            org.mockito.Mockito.reset(serverValidator);
            when(serverValidator.isValid(anyString())).thenReturn(true);
            payFastPaymentService.handleItn(itn);
            assertThat(paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0).getStatus())
                    .isEqualTo(PaymentRecordStatus.PAID);
        } finally {
            payFastProperties.setValidateWithServer(false);
        }
    }

    @Test
    void cancelledItnReleasesTheUnpaidRental() throws Exception {
        String session = "pf-rent-" + UUID.randomUUID();
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Map<String, Object> add = new LinkedHashMap<>();
        add.put("listingId", tractor.getId());
        add.put("quantity", 1);
        add.put("rentalStart", start.toString());
        add.put("rentalEnd", start.plus(2, ChronoUnit.DAYS).toString());
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", session)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(add)))
                .andExpect(status().isOk());
        JsonNode res = checkout(session, "CARD");
        String ref = res.path("paymentRef").asText();
        PaymentRecord pr = paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0);
        String amount = pr.getAmount().setScale(2).toPlainString();
        assertThat(pr.getRentalBooking().getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        payFastPaymentService.handleItn(signed(itn(ref, amount, "CANCELLED", "ORDER")));

        PaymentRecord after = paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0);
        assertThat(after.getRentalBooking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(after.getStatus()).isEqualTo(PaymentRecordStatus.PENDING_PAYMENT);
        assertThat(after.getSettlementStatus()).isEqualTo(SettlementStatus.NONE);
        mockMvc.perform(get("/api/public/payments/status").param("ref", ref))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void paymentConfirmedAfterTheOrderWasCancelledIsRefundDue() throws Exception {
        String ref = checkoutSaleOrder("CARD").path("paymentRef").asText();
        PaymentRecord pr = paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0);
        pr.getOrder().setStatus(OrderStatus.CANCELLED); // e.g. released by the unpaid-order job

        payFastPaymentService.handleItn(signed(itn(ref, "100.00", "COMPLETE", "ORDER")));

        PaymentRecord after = paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0);
        assertThat(after.getStatus()).isEqualTo(PaymentRecordStatus.PAID);
        assertThat(after.getOrder().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(after.getSettlementStatus()).isEqualTo(SettlementStatus.REFUND_DUE);
    }

    @Test
    void notifyEndpointAnswers200AndProcessesFormFieldsInOrder() throws Exception {
        String ref = checkoutSaleOrder("CARD").path("paymentRef").asText();

        Map<String, String> forged = signed(itn(ref, "100.00", "COMPLETE", "ORDER"));
        forged.put("signature", "0123456789abcdef0123456789abcdef");
        mockMvc.perform(form(forged)).andExpect(status().isOk()).andExpect(content().string(""));
        assertThat(paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0).getStatus())
                .isEqualTo(PaymentRecordStatus.PENDING_PAYMENT);

        mockMvc.perform(form(signed(itn(ref, "100.00", "COMPLETE", "ORDER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        assertThat(paymentRecordRepository.findAllByGatewayMerchantRef(ref).get(0).getStatus())
                .isEqualTo(PaymentRecordStatus.PAID);
        verify(serverValidator, never()).isValid(anyString());
    }

    // ---- Subscriptions ----

    @Test
    void subscriptionIsActivatedByItn() {
        SubscriptionActivationIntent intent = new SubscriptionActivationIntent();
        intent.setProvider(provider);
        intent.setPlan(SubscriptionPlan.BASIC);
        intent.setBillingCycle(BillingCycle.MONTHLY);
        intent.setAmountDue(new BigDecimal("199.00"));
        intent.setBaseMonthly(new BigDecimal("199.00"));
        intent.setUsageFeePercent(BigDecimal.ZERO);
        intent.setPaidTransactionsCount(0);
        intent.setPaidOrderTotalsSum(BigDecimal.ZERO);
        intent.setUsageFeesTotal(BigDecimal.ZERO);
        intent.setUsageWindowStart(Instant.now());
        intent.setPaymentReference("SUB-PF-" + UUID.randomUUID().toString().substring(0, 6));
        intent = intentRepository.saveAndFlush(intent);

        var checkout = payFastPaymentService.initiateSubscriptionCheckout(
                provider.getId(), intent.getId(), PeachPaymentMethod.CARD, "owner@green.test");
        assertThat(checkout.fields().get("custom_str1")).isEqualTo("SUB");
        assertThat(checkout.fields().get("amount")).isEqualTo("199.00");
        assertThat(checkout.fields().get("item_name")).isEqualTo("Marketplace Basic subscription");
        assertThat(checkout.fields().get("return_url")).endsWith("/provider/subscription?payRef=" + checkout.paymentRef());
        String ref = checkout.paymentRef();

        // An ITN for the wrong kind (ORDER) or amount is rejected.
        assertRejected(signed(itn(ref, "199.00", "COMPLETE", "ORDER")), "not a PayFast subscription");
        assertRejected(signed(itn(ref, "19.90", "COMPLETE", "SUB")), "amount does not match");

        Map<String, String> ok = signed(itn(ref, "199.00", "COMPLETE", "SUB"));
        payFastPaymentService.handleItn(ok);
        payFastPaymentService.handleItn(ok);

        SubscriptionActivationIntent used = intentRepository.findById(intent.getId()).orElseThrow();
        assertThat(used.isUsed()).isTrue();
        assertThat(used.getGatewayCheckoutId()).isEqualTo("1089250");
        assertThat(subscriptionRepository.findActiveForProviderOrderByExpiresAtDesc(
                        provider.getId(), SubscriptionStatus.ACTIVE, Instant.now()))
                .hasSize(1);
    }

    // ---- helpers ----

    private void assertRejected(Map<String, String> itn, String message) {
        assertThatThrownBy(() -> payFastPaymentService.handleItn(itn))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(message);
    }

    private JsonNode checkoutSaleOrder(String method) throws Exception {
        String session = "pf-" + UUID.randomUUID();
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", session)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", hay.getId(), "quantity", 2))))
                .andExpect(status().isOk());
        return checkout(session, method);
    }

    private JsonNode checkout(String session, String method) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guestName", "Thandi Mokoena");
        body.put("guestEmail", "thandi+" + UUID.randomUUID().toString().substring(0, 6) + "@example.com");
        body.put("guestPhone", "0820000000");
        body.put("deliveryOrPickup", "Collect at gate 3");
        body.put("paymentMethod", "PEACH");
        body.put("peachPaymentMethod", method);
        String json = mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", session)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private static Map<String, String> fields(JsonNode res) {
        Map<String, String> out = new LinkedHashMap<>();
        res.at("/payfast/fields").fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
        return out;
    }

    /** ITN in PayFast's field order, with the blank fields a real ITN carries. */
    private static Map<String, String> itn(String ref, String amountGross, String status, String kind) {
        Map<String, String> itn = new LinkedHashMap<>();
        itn.put("m_payment_id", ref);
        itn.put("pf_payment_id", "1089250");
        itn.put("payment_status", status);
        itn.put("item_name", "Green Acres order");
        itn.put("item_description", "");
        itn.put("amount_gross", amountGross);
        itn.put("amount_fee", "-2.30");
        itn.put("amount_net", "97.70");
        itn.put("custom_str1", kind);
        itn.put("custom_str2", "");
        itn.put("custom_str3", "");
        itn.put("custom_str4", "");
        itn.put("custom_str5", "");
        itn.put("custom_int1", "");
        itn.put("custom_int2", "");
        itn.put("custom_int3", "");
        itn.put("custom_int4", "");
        itn.put("custom_int5", "");
        itn.put("name_first", "Thandi");
        itn.put("name_last", "");
        itn.put("email_address", "thandi@example.com");
        itn.put("merchant_id", "10000100");
        return itn;
    }

    private static Map<String, String> signed(Map<String, String> itn) {
        Map<String, String> out = new LinkedHashMap<>(itn);
        out.put("signature", PayFastSignatureUtil.itnSignature(itn, PASSPHRASE));
        return out;
    }

    private static MockHttpServletRequestBuilder form(Map<String, String> params) {
        MockHttpServletRequestBuilder req = post("/api/public/payfast/notify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        params.forEach(req::param);
        return req;
    }
}
