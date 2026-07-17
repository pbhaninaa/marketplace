package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.SubscriptionActivationIntent;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.SubscriptionActivationIntentRepository;
import com.agrimarket.repo.SubscriptionRepository;
import com.agrimarket.support.TestFixtures;
import com.agrimarket.util.PeachSignatureUtil;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "peach.enabled=true",
    "peach.client-id=test-client",
    "peach.client-secret=test-secret",
    "peach.merchant-id=test-merchant",
    "peach.entity-id=test-entity",
    "peach.secret-token=webhook-signing-secret"
})
class PeachSubscriptionWebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired private PeachPaymentService peachPaymentService;
    @Autowired private SubscriptionActivationIntentRepository intentRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private TestFixtures fixtures;

    @Test
    void onlyVerifiedCallbackActivatesOnce() {
        Provider provider = fixtures.saveActiveProvider("Callback Provider", "callback-provider");
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
        intent.setPaymentReference("SUB-PEACH-ONLY");
        intent.setGatewayMerchantRef("Ref1234567890123");
        intent.setGatewayCheckoutId("checkout-123");
        intent = intentRepository.saveAndFlush(intent);

        Map<String, String> callback = successfulCallback();
        callback.put("signature", "invalid");
        assertThatThrownBy(() -> peachPaymentService.handleWebhook(callback))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid Peach signature");
        assertThat(intentRepository.findById(intent.getId()).orElseThrow().isUsed()).isFalse();
        assertThat(subscriptionRepository.findTopByProviderIdOrderByCreatedAtDesc(provider.getId())).isEmpty();

        callback.put("signature", PeachSignatureUtil.buildSignature(callback, "webhook-signing-secret"));
        peachPaymentService.handleWebhook(callback);
        peachPaymentService.handleWebhook(callback);

        assertThat(intentRepository.findById(intent.getId()).orElseThrow().isUsed()).isTrue();
        assertThat(subscriptionRepository
                        .findActiveForProviderOrderByExpiresAtDesc(
                                provider.getId(), SubscriptionStatus.ACTIVE, Instant.now()))
                .hasSize(1);
    }

    private static Map<String, String> successfulCallback() {
        Map<String, String> callback = new LinkedHashMap<>();
        callback.put("merchantTransactionId", "Ref1234567890123");
        callback.put("checkoutId", "checkout-123");
        callback.put("amount", "199.00");
        callback.put("currency", "ZAR");
        callback.put("paymentType", "DB");
        callback.put("authentication.entityId", "test-entity");
        callback.put("status", "successful");
        callback.put("result.code", "000.100.110");
        return callback;
    }
}
