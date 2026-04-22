package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.PurchaseOrderRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Purchase flow: stock is reserved at checkout, finalized only on provider PAID; meetup code must be recorded first.
 */
class ProviderPurchaseOrderFlowMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String sessionId;
    private Listing listing;
    private String providerToken;
    private Long orderId;
    private String verificationCode;

    @BeforeEach
    void seed() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        sessionId = "prov-po-session-" + u;
        Category cat = fixtures.saveCategory("Produce", "produce-" + u);
        Provider p = fixtures.saveActiveProvider("PO Flow Co", "po-flow-" + u);
        fixtures.saveActiveSubscription(p);
        listing = fixtures.saveSaleListing(p, cat, "Last bag", new BigDecimal("10"), 1);

        UserAccount owner = new UserAccount(
                "po-owner-" + u + "@integration.test",
                passwordEncoder.encode("SecurePass123!"),
                UserRole.PROVIDER_OWNER,
                p);
        userAccountRepository.save(owner);
        providerToken = jwtService.createToken(owner.getId(), owner.getEmail(), owner.getRole(), p.getId());

        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("listingId", listing.getId(), "quantity", 1))))
                .andExpect(status().isOk());

        MvcResult checkout = mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "guestName", "Guest",
                                "guestEmail", "guest+" + u + "@test.test",
                                "guestPhone", "0820000000",
                                "deliveryOrPickup", "Collect",
                                "paymentMethod", "EFT"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(checkout.getResponse().getContentAsString());
        orderId = body.get("purchaseOrderIds").get(0).asLong();
        verificationCode = body.get("verificationCodes").get(0).asText();
    }

    @Test
    void confirmPayment_withoutVerify_returns400() throws Exception {
        mockMvc.perform(put("/api/provider/me/orders/purchases/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                        .param("status", "PAID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verify_then_confirmPaid_deletesListingWhenSoldOut() throws Exception {
        mockMvc.perform(post("/api/provider/me/verify/order/" + verificationCode)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/provider/me/orders/purchases/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                        .param("status", "PAID"))
                .andExpect(status().isOk());

        assertThat(listingRepository.findById(listing.getId())).isEmpty();
        assertThat(purchaseOrderRepository.findById(orderId)).isPresent();
    }

    @Test
    void rejectOrder_releasesReservation_listingRemains() throws Exception {
        mockMvc.perform(put("/api/provider/me/orders/purchases/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk());

        Listing reloaded = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(1);
        assertThat(reloaded.getReservedStock() == null ? 0 : reloaded.getReservedStock()).isZero();
    }
}
