package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.Referral;
import com.agrimarket.domain.ReferralSource;
import com.agrimarket.domain.ReferralStatus;
import com.agrimarket.domain.SubscriptionActivationIntent;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.ReferralRepository;
import com.agrimarket.repo.SubscriptionActivationIntentRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import com.agrimarket.util.PeachSignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {
    "peach.enabled=true",
    "peach.client-id=test-client",
    "peach.client-secret=test-secret",
    "peach.merchant-id=test-merchant",
    "peach.entity-id=test-entity",
    "peach.secret-token=webhook-signing-secret"
})
class ReferralServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "webhook-signing-secret";

    @Autowired private ReferralService referralService;
    @Autowired private PeachPaymentService peachPaymentService;
    @Autowired private ReferralRepository referralRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private SubscriptionActivationIntentRepository intentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private TestFixtures fixtures;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ---------------- helpers ----------------

    private Provider provider(String name) {
        Provider p = fixtures.saveActiveProvider(name, name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID());
        p.setCreatedAt(Instant.now());
        return providerRepository.save(p);
    }

    private UserAccount user(Provider provider, UserRole role) {
        UserAccount u = new UserAccount(
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@ref.test", passwordEncoder.encode("x"), role, provider);
        return userAccountRepository.save(u);
    }

    private String token(UserAccount u) {
        return "Bearer " + jwtService.createToken(
                u.getId(), u.getEmail(), u.getRole(), u.getProvider() != null ? u.getProvider().getId() : null);
    }

    private SubscriptionActivationIntent intent(Provider provider, String ref, String checkoutId, String amount) {
        SubscriptionActivationIntent intent = new SubscriptionActivationIntent();
        intent.setProvider(provider);
        intent.setPlan(SubscriptionPlan.BASIC);
        intent.setBillingCycle(BillingCycle.MONTHLY);
        intent.setAmountDue(new BigDecimal(amount));
        intent.setBaseMonthly(new BigDecimal(amount));
        intent.setUsageFeePercent(BigDecimal.ZERO);
        intent.setPaidTransactionsCount(0);
        intent.setPaidOrderTotalsSum(BigDecimal.ZERO);
        intent.setUsageFeesTotal(BigDecimal.ZERO);
        intent.setUsageWindowStart(Instant.now());
        intent.setPaymentReference("SUB-" + ref);
        intent.setGatewayMerchantRef(ref);
        intent.setGatewayCheckoutId(checkoutId);
        return intentRepository.saveAndFlush(intent);
    }

    private static Map<String, String> signedCallback(String ref, String checkoutId, String amount) {
        Map<String, String> callback = new LinkedHashMap<>();
        callback.put("merchantTransactionId", ref);
        callback.put("checkoutId", checkoutId);
        callback.put("amount", amount);
        callback.put("currency", "ZAR");
        callback.put("paymentType", "DB");
        callback.put("authentication.entityId", "test-entity");
        callback.put("status", "successful");
        callback.put("result.code", "000.100.110");
        callback.put("signature", PeachSignatureUtil.buildSignature(callback, SECRET));
        return callback;
    }

    // ---------------- capture at registration ----------------

    @Test
    void registrationWithCodeRecordsReferral_crossDevice() throws Exception {
        userAccountRepository.save(new UserAccount(
                "admin-" + UUID.randomUUID() + "@ref.test", passwordEncoder.encode("x"), UserRole.PLATFORM_ADMIN, null));
        Provider referrerBiz = provider("Green Acres");
        UserAccount referrer = user(referrerBiz, UserRole.PROVIDER_OWNER);
        String code = referralService.getOrCreateCode(referrer.getId());

        String email = "new-" + UUID.randomUUID() + "@ref.test";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("businessName", "Sunrise Farm");
        body.put("description", "Veg");
        body.put("location", "Durban");
        body.put("ownerEmail", email);
        body.put("password", "SecurePass123");
        body.put("referralCode", " " + code.toLowerCase() + " "); // typed by hand on another device
        mockMvc.perform(post("/api/public/provider/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        UserAccount owner = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        Referral r = referralRepository.findByRefereeProvider_Id(owner.getProvider().getId()).orElseThrow();
        assertThat(r.getReferrer().getId()).isEqualTo(referrer.getId());
        assertThat(r.getSource()).isEqualTo(ReferralSource.REGISTRATION);
        assertThat(r.getStatus()).isEqualTo(ReferralStatus.PENDING);

        mockMvc.perform(get("/api/public/referrals/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.referrerName").value("Green Acres"));
    }

    @Test
    void registrationWithUnknownCodeIsRejected() throws Exception {
        userAccountRepository.save(new UserAccount(
                "admin-" + UUID.randomUUID() + "@ref.test", passwordEncoder.encode("x"), UserRole.PLATFORM_ADMIN, null));
        String email = "bad-" + UUID.randomUUID() + "@ref.test";
        String json = objectMapper.writeValueAsString(Map.of(
                "businessName", "Typo Farm",
                "description", "Veg",
                "location", "Durban",
                "ownerEmail", email,
                "password", "SecurePass123",
                "referralCode", "NOSUCH999"));
        mockMvc.perform(post("/api/public/provider/register").contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REFERRAL_CODE"));
        assertThat(userAccountRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    // ---------------- claim rules ----------------

    @Test
    void selfReferralIsRejected_ownCodeAndTeammateCode() {
        Provider biz = provider("Self Farm");
        UserAccount owner = user(biz, UserRole.PROVIDER_OWNER);
        UserAccount staff = user(biz, UserRole.PROVIDER_STAFF);

        String ownCode = referralService.getOrCreateCode(owner.getId());
        assertThatThrownBy(() -> referralService.claim(owner.getId(), ownCode))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("SELF_REFERRAL");

        String teammateCode = referralService.getOrCreateCode(staff.getId());
        assertThatThrownBy(() -> referralService.claim(owner.getId(), teammateCode))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("SELF_REFERRAL");
        assertThat(referralRepository.findByRefereeProvider_Id(biz.getId())).isEmpty();
    }

    @Test
    void oneReferralPerBusiness_duplicateRejected_sameReferrerIdempotent() {
        UserAccount alice = user(provider("Alice Farm"), UserRole.PROVIDER_OWNER);
        UserAccount bob = user(provider("Bob Farm"), UserRole.PROVIDER_OWNER);
        Provider newBiz = provider("New Farm");
        UserAccount owner = user(newBiz, UserRole.PROVIDER_OWNER);

        Referral first = referralService.claim(owner.getId(), referralService.getOrCreateCode(alice.getId()));
        Referral again = referralService.claim(owner.getId(), referralService.getOrCreateCode(alice.getId()));
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(first.getSource()).isEqualTo(ReferralSource.CLAIM);

        String bobCode = referralService.getOrCreateCode(bob.getId());
        assertThatThrownBy(() -> referralService.claim(owner.getId(), bobCode))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("ALREADY_REFERRED");
        assertThat(referralRepository.findByRefereeProvider_Id(newBiz.getId()).orElseThrow().getReferrer().getId())
                .isEqualTo(alice.getId());
    }

    @Test
    void claimOutsideWindowIsRejected() {
        referralService.updateSettings(new BigDecimal("15"), 14, "test");
        UserAccount referrer = user(provider("Window Referrer"), UserRole.PROVIDER_OWNER);
        Provider oldBiz = provider("Old Farm");
        oldBiz.setCreatedAt(Instant.now().minus(15, ChronoUnit.DAYS));
        providerRepository.save(oldBiz);
        UserAccount owner = user(oldBiz, UserRole.PROVIDER_OWNER);

        String code = referralService.getOrCreateCode(referrer.getId());
        assertThatThrownBy(() -> referralService.claim(owner.getId(), code))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("REFERRAL_CLAIM_CLOSED");

        // Inside the window it works.
        oldBiz.setCreatedAt(Instant.now().minus(13, ChronoUnit.DAYS));
        providerRepository.save(oldBiz);
        assertThat(referralService.claim(owner.getId(), code).getStatus()).isEqualTo(ReferralStatus.PENDING);
    }

    @Test
    void claimAfterFirstSubscriptionIsRejected() {
        UserAccount referrer = user(provider("Sub Referrer"), UserRole.PROVIDER_OWNER);
        Provider biz = provider("Paid Farm");
        fixtures.saveActiveSubscription(biz);
        UserAccount owner = user(biz, UserRole.PROVIDER_OWNER);
        String code = referralService.getOrCreateCode(referrer.getId());
        assertThatThrownBy(() -> referralService.claim(owner.getId(), code))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("REFERRAL_CLAIM_CLOSED");
    }

    @Test
    void staffCannotClaimForBusiness() {
        UserAccount referrer = user(provider("Staff Referrer"), UserRole.PROVIDER_OWNER);
        UserAccount staff = user(provider("Staff Farm"), UserRole.PROVIDER_STAFF);
        String code = referralService.getOrCreateCode(referrer.getId());
        assertThatThrownBy(() -> referralService.claim(staff.getId(), code))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .hasToString("403 FORBIDDEN");
    }

    // ---------------- commission on Peach confirmation ----------------

    @Test
    void webhookRetriesRecordCommissionOnce_atFrozenRate() {
        referralService.updateSettings(new BigDecimal("15"), 30, "test");
        UserAccount referrer = user(provider("Pay Referrer"), UserRole.PROVIDER_OWNER);
        Provider biz = provider("Paying Farm");
        UserAccount owner = user(biz, UserRole.PROVIDER_OWNER);
        referralService.claim(owner.getId(), referralService.getOrCreateCode(referrer.getId()));

        intent(biz, "RefA234567890123", "checkout-a", "199.00");
        Map<String, String> callback = signedCallback("RefA234567890123", "checkout-a", "199.00");
        peachPaymentService.handleWebhook(callback);
        peachPaymentService.handleWebhook(callback); // Peach retry
        peachPaymentService.handleWebhook(callback); // redirect + webhook both arrive

        Referral r = referralRepository.findByRefereeProvider_Id(biz.getId()).orElseThrow();
        assertThat(r.getStatus()).isEqualTo(ReferralStatus.EARNED);
        assertThat(r.getCommissionAmount()).isEqualByComparingTo("29.85");
        assertThat(r.getCommissionPercent()).isEqualByComparingTo("15");
        assertThat(r.getQualifyingPaymentRef()).isEqualTo("RefA234567890123");
        Instant earnedAt = r.getEarnedAt();

        // Direct replay of the same payment is a no-op too.
        referralService.recordQualifyingPayment(biz.getId(), r.getQualifyingSubscriptionId(), new BigDecimal("199.00"), "RefA234567890123");

        // Rate change later never rewrites earned commission; a renewal earns nothing more.
        referralService.updateSettings(new BigDecimal("50"), 30, "test");
        intent(biz, "RefB234567890123", "checkout-b", "499.00");
        peachPaymentService.handleWebhook(signedCallback("RefB234567890123", "checkout-b", "499.00"));

        Referral after = referralRepository.findByRefereeProvider_Id(biz.getId()).orElseThrow();
        assertThat(after.getCommissionAmount()).isEqualByComparingTo("29.85");
        assertThat(after.getCommissionPercent()).isEqualByComparingTo("15");
        assertThat(after.getEarnedAt()).isEqualTo(earnedAt);
        assertThat(referralService.adminStats().awaitingPayoutAmount()).isEqualByComparingTo("29.85");
    }

    @Test
    void paymentWithoutReferralIsUnaffected() {
        Provider biz = provider("Solo Farm");
        intent(biz, "RefC234567890123", "checkout-c", "199.00");
        peachPaymentService.handleWebhook(signedCallback("RefC234567890123", "checkout-c", "199.00"));
        assertThat(referralRepository.findByRefereeProvider_Id(biz.getId())).isEmpty();
        assertThat(intentRepository.findByGatewayMerchantRef("RefC234567890123").orElseThrow().isUsed()).isTrue();
    }

    @Test
    void markPaidOnlyAfterEarned() {
        UserAccount referrer = user(provider("Payout Referrer"), UserRole.PROVIDER_OWNER);
        Provider biz = provider("Payout Farm");
        UserAccount owner = user(biz, UserRole.PROVIDER_OWNER);
        Referral r = referralService.claim(owner.getId(), referralService.getOrCreateCode(referrer.getId()));
        assertThatThrownBy(() -> referralService.markPaid(r.getId(), null, "admin"))
                .isInstanceOf(ApiException.class);

        referralService.recordQualifyingPayment(biz.getId(), null, new BigDecimal("100.00"), "RefD");
        Referral paid = referralService.markPaid(r.getId(), "EFT 123", "admin@ref.test");
        assertThat(paid.getStatus()).isEqualTo(ReferralStatus.PAID);
        assertThatThrownBy(() -> referralService.markPaid(r.getId(), null, "admin"))
                .isInstanceOf(ApiException.class);
    }

    // ---------------- permissions ----------------

    @Test
    void onlyPlatformAdminsChangeReferralSettings() throws Exception {
        UserAccount owner = user(provider("Perm Farm"), UserRole.PROVIDER_OWNER);
        UserAccount support = userAccountRepository.save(new UserAccount(
                "support-" + UUID.randomUUID() + "@ref.test", passwordEncoder.encode("x"), UserRole.SUPPORT, null));
        UserAccount admin = userAccountRepository.save(new UserAccount(
                "admin-" + UUID.randomUUID() + "@ref.test", passwordEncoder.encode("x"), UserRole.PLATFORM_ADMIN, null));
        String body = "{\"commissionPercent\":20,\"claimWindowDays\":10}";

        mockMvc.perform(put("/api/admin/referrals/settings").header(HttpHeaders.AUTHORIZATION, token(owner))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/referrals/settings").header(HttpHeaders.AUTHORIZATION, token(support))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/referrals/settings").header(HttpHeaders.AUTHORIZATION, token(admin))
                        .contentType(APPLICATION_JSON).content("{\"commissionPercent\":120,\"claimWindowDays\":10}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/referrals/settings").header(HttpHeaders.AUTHORIZATION, token(admin))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionPercent").value(20))
                .andExpect(jsonPath("$.claimWindowDays").value(10))
                .andExpect(jsonPath("$.updatedBy").value(admin.getEmail()));

        mockMvc.perform(get("/api/admin/referrals").header(HttpHeaders.AUTHORIZATION, token(admin))
                        .param("referrer", "nobody").param("from", "2026-01-01").param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void myReferralsPanelShowsReferredBusinessesWithoutEmails() throws Exception {
        UserAccount referrer = user(provider("Panel Referrer"), UserRole.PROVIDER_OWNER);
        Provider biz = provider("Panel Farm");
        UserAccount owner = user(biz, UserRole.PROVIDER_OWNER);
        referralService.claim(owner.getId(), referralService.getOrCreateCode(referrer.getId()));

        mockMvc.perform(get("/api/provider/referrals").header(HttpHeaders.AUTHORIZATION, token(referrer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitedCount").value(1))
                .andExpect(jsonPath("$.referrals[0].businessName").value("Panel Farm"))
                .andExpect(jsonPath("$.referrals[0].refereeEmail").doesNotExist());
        mockMvc.perform(get("/api/provider/referrals").header(HttpHeaders.AUTHORIZATION, token(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invite.referredByName").value("Panel Referrer"))
                .andExpect(jsonPath("$.invite.canClaim").value(false));
    }
}
