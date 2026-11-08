package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.PaymentGateway;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.SettlementStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.service.SellerSettlementService;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class SellerPayoutsMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private Provider provider;
    private String adminToken;
    private String adminEmail;
    private String providerToken;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        provider = fixtures.saveActiveProvider("Payout Farm", "payout-" + u);
        fixtures.saveActiveSubscription(provider);

        adminEmail = "admin-" + u + "@payouts.test";
        UserAccount admin = userAccountRepository.save(
                new UserAccount(adminEmail, passwordEncoder.encode("x"), UserRole.PLATFORM_ADMIN, null));
        adminToken = jwtService.createToken(admin.getId(), admin.getEmail(), UserRole.PLATFORM_ADMIN, null);

        UserAccount owner = userAccountRepository.save(new UserAccount(
                "owner-" + u + "@payouts.test", passwordEncoder.encode("x"), UserRole.PROVIDER_OWNER, provider));
        providerToken = jwtService.createToken(owner.getId(), owner.getEmail(), UserRole.PROVIDER_OWNER, provider.getId());
        userAccountRepository.flush();
    }

    private PaymentRecord payment(String amount, SettlementStatus settlement) {
        PaymentRecord pr = new PaymentRecord();
        pr.setProvider(provider);
        pr.setMethod(PaymentMethod.PEACH);
        pr.setGateway(PaymentGateway.PAYFAST);
        pr.setStatus(PaymentRecordStatus.PAID);
        pr.setAmount(new BigDecimal(amount));
        BigDecimal fee = SellerSettlementService.platformFee(pr.getAmount(), new BigDecimal("5"));
        pr.setPlatformFeePercent(new BigDecimal("5"));
        pr.setPlatformFee(fee);
        pr.setSellerPayoutAmount(pr.getAmount().subtract(fee));
        pr.setSettlementStatus(settlement);
        pr.setPaidAt(Instant.now());
        return paymentRecordRepository.save(pr);
    }

    @Test
    void feeIsRoundedHalfUpToCents() {
        assertThat(SellerSettlementService.platformFee(new BigDecimal("100.10"), new BigDecimal("2.5")))
                .isEqualByComparingTo("2.50"); // 2.5025
        assertThat(SellerSettlementService.platformFee(new BigDecimal("0.30"), new BigDecimal("5")))
                .isEqualByComparingTo("0.02"); // 0.015
        assertThat(SellerSettlementService.platformFee(new BigDecimal("80.00"), BigDecimal.ZERO))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void adminSeesWhatIsOwedAndMarksItPaidOut() throws Exception {
        PaymentRecord a = payment("100.00", SettlementStatus.HELD);
        PaymentRecord b = payment("40.00", SettlementStatus.HELD);

        mockMvc.perform(get("/api/admin/payouts/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellers[?(@.providerId == " + provider.getId() + ")].amountOwed").value(133.0));

        mockMvc.perform(get("/api/admin/payouts/payments")
                        .param("providerId", provider.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Only the selected payment.
        mockMvc.perform(post("/api/admin/payouts/mark-paid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", provider.getId(), "paymentIds", List.of(a.getId()), "reference", "FNB-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.total").value(95.0));

        PaymentRecord paid = paymentRecordRepository.findById(a.getId()).orElseThrow();
        assertThat(paid.getSettlementStatus()).isEqualTo(SettlementStatus.PAID_OUT);
        assertThat(paid.getPayoutReference()).isEqualTo("FNB-123");
        assertThat(paid.getPaidOutBy()).isEqualTo(adminEmail);
        assertThat(paid.getPaidOutAt()).isNotNull();
        assertThat(paymentRecordRepository.findById(b.getId()).orElseThrow().getSettlementStatus())
                .isEqualTo(SettlementStatus.HELD);

        // Paying the same payment twice is refused.
        mockMvc.perform(post("/api/admin/payouts/mark-paid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", provider.getId(), "paymentIds", List.of(a.getId()), "reference", "again"))))
                .andExpect(status().isConflict());

        // Everything still held for the seller.
        mockMvc.perform(post("/api/admin/payouts/mark-paid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", provider.getId(), "reference", "FNB-124"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        assertThat(paymentRecordRepository.findById(b.getId()).orElseThrow().getSettlementStatus())
                .isEqualTo(SettlementStatus.PAID_OUT);
    }

    @Test
    void refundDuePaymentsAreMarkedRefunded() throws Exception {
        PaymentRecord r = payment("60.00", SettlementStatus.REFUND_DUE);
        PaymentRecord held = payment("10.00", SettlementStatus.HELD);

        mockMvc.perform(post("/api/admin/payouts/mark-refunded")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "paymentIds", List.of(held.getId()), "reference", "RF-1"))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/payouts/mark-refunded")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "paymentIds", List.of(r.getId()), "reference", "RF-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(60.0));
        assertThat(paymentRecordRepository.findById(r.getId()).orElseThrow().getSettlementStatus())
                .isEqualTo(SettlementStatus.REFUNDED);
    }

    @Test
    void providersCannotUseAdminPayoutsButSeeTheirOwn() throws Exception {
        payment("100.00", SettlementStatus.HELD);

        mockMvc.perform(post("/api/admin/payouts/mark-paid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "providerId", provider.getId(), "reference", "self"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/payouts/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/provider/me/payouts").header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountOwed").value(95.0))
                .andExpect(jsonPath("$.payments[0].settlementStatus").value("HELD"))
                .andExpect(jsonPath("$.payments[0].paidOutBy").isEmpty());
    }
}
