package com.agrimarket.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Provider;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class CheckoutMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestFixtures fixtures;

    private String sessionId;
    private Listing listing;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        sessionId = "checkout-session-" + u;
        Category cat = fixtures.saveCategory("Equipment", "eq-" + u);
        Provider p = fixtures.saveActiveProvider("Checkout Co", "checkout-co-" + u);
        fixtures.saveActiveSubscription(p);
        listing = fixtures.saveSaleListing(p, cat, "Bale of hay", new BigDecimal("50"), 20);
    }

    @Test
    void guestCheckout_createsOrder() throws Exception {
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("listingId", listing.getId(), "quantity", 2))))
                .andExpect(status().isOk());

        Map<String, Object> checkoutBody = Map.of(
                "guestName", "Test Guest",
                "guestEmail", "guest+" + UUID.randomUUID() + "@checkout.test",
                "guestPhone", "0820000000",
                "deliveryOrPickup", "Collect at gate 3",
                "paymentMethod", "EFT");

        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseOrderIds").isArray())
                .andExpect(jsonPath("$.providerId").value(listing.getProvider().getId().intValue()));
    }
}
