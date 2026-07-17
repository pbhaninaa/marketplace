package com.agrimarket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionPaymentProof;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionProofStatus;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.SubscriptionPaymentProofRepository;
import com.agrimarket.repo.SubscriptionRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class ProviderSubscriptionProofFlowMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionPaymentProofRepository proofRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private TestFixtures fixtures;

    @Test
    void retiredProviderManualPaymentRoutesReturnGone() throws Exception {
        Provider provider = fixtures.saveActiveProvider("Peach Only", "peach-only-" + UUID.randomUUID());
        String token = tokenFor(UserRole.PROVIDER_OWNER, provider);

        mockMvc.perform(get("/api/provider/me/subscription/bank-details")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_PAYMENT_RETIRED"));

        MockMultipartFile proof = new MockMultipartFile(
                "file", "proof.pdf", MediaType.APPLICATION_PDF_VALUE, "retired".getBytes());
        mockMvc.perform(multipart("/api/provider/me/subscription/proof")
                        .file(proof)
                        .param("intentId", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_PAYMENT_RETIRED"));

        mockMvc.perform(post("/api/provider/me/subscription/select")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"BASIC\",\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_PAYMENT_RETIRED"));
    }

    @Test
    void decisionsReturnGoneWhileHistoricalProofFilesRemainReadable() throws Exception {
        Provider provider = fixtures.saveActiveProvider("Historical Proof", "historical-proof-" + UUID.randomUUID());
        Subscription subscription = new Subscription();
        subscription.setProvider(provider);
        subscription.setPlan(SubscriptionPlan.BASIC);
        subscription.setBillingCycle(BillingCycle.MONTHLY);
        subscription.setStatus(SubscriptionStatus.REJECTED);
        subscription.setAmountDue(new BigDecimal("199.00"));
        subscription.setPaymentReference("LEGACY-REFERENCE");
        subscription.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        subscription = subscriptionRepository.save(subscription);

        byte[] historicalBytes = "historical proof".getBytes();
        SubscriptionPaymentProof proof = new SubscriptionPaymentProof();
        proof.setProvider(provider);
        proof.setSubscription(subscription);
        proof.setContentType(MediaType.APPLICATION_PDF_VALUE);
        proof.setData(historicalBytes);
        proof.setStatus(SubscriptionProofStatus.REJECTED);
        proof = proofRepository.save(proof);

        String adminToken = tokenFor(UserRole.PLATFORM_ADMIN, null);
        String supportToken = tokenFor(UserRole.SUPPORT, null);

        mockMvc.perform(get("/api/admin/subscription-proofs/" + proof.getId() + "/file")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(historicalBytes));
        mockMvc.perform(get("/api/support/subscription-proofs/" + proof.getId() + "/file")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supportToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(historicalBytes));

        String decision = "{\"approve\":true,\"note\":\"must stay retired\"}";
        mockMvc.perform(post("/api/admin/subscription-proofs/" + proof.getId() + "/decide")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision))
                .andExpect(status().isGone());
        mockMvc.perform(post("/api/support/subscription-proofs/" + proof.getId() + "/decide")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supportToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decision))
                .andExpect(status().isGone());
    }

    private String tokenFor(UserRole role, Provider provider) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@integration.test";
        UserAccount user = new UserAccount(email, passwordEncoder.encode("irrelevant"), role, provider);
        userAccountRepository.saveAndFlush(user);
        return jwtService.createToken(
                user.getId(), user.getEmail(), user.getRole(), provider == null ? null : provider.getId());
    }
}
