package com.agrimarket.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

class CartMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestFixtures fixtures;

    private String sessionId;
    private Listing listingA;
    private Listing listingB;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        sessionId = "test-session-" + u;
        Category cat = fixtures.saveCategory("Livestock", "live-" + u);
        Provider p1 = fixtures.saveActiveProvider("Alpha Farm", "alpha-" + u);
        Provider p2 = fixtures.saveActiveProvider("Beta Hire", "beta-" + u);
        fixtures.saveActiveSubscription(p1);
        fixtures.saveActiveSubscription(p2);
        listingA = fixtures.saveSaleListing(p1, cat, "Heifer A", new BigDecimal("100"), 10);
        listingB = fixtures.saveSaleListing(p2, cat, "Trailer B", new BigDecimal("200"), 3);
    }

    @Test
    void addToCart_fromProviderThatCannotTakeOrders_isRejected() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Category cat = fixtures.saveCategory("Feed", "feed-" + u);
        Provider lapsed = fixtures.saveActiveProviderWithTrialEndingAt(
                "Lapsed Farm", "lapsed-" + u, java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
        Listing stale = fixtures.saveSaleListing(lapsed, cat, "Old Hay", new BigDecimal("50"), 5);

        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", stale.getId(), "quantity", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"));
    }

    @Test
    void addToCart_fromTwoSellers_groupsItemsPerSeller() throws Exception {
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("listingId", listingA.getId(), "quantity", 2))))
                .andExpect(status().isOk())
                // One seller: older clients still get the seller in the lockedProvider* fields.
                .andExpect(jsonPath("$.lockedProviderId").value(listingA.getProvider().getId().intValue()))
                .andExpect(jsonPath("$.sellers.length()").value(1));

        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("listingId", listingB.getId(), "quantity", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedProviderId").doesNotExist())
                .andExpect(jsonPath("$.sellers.length()").value(2))
                .andExpect(jsonPath("$.sellers[0].providerName").value("Alpha Farm"))
                .andExpect(jsonPath("$.sellers[0].itemCount").value(2))
                .andExpect(jsonPath("$.sellers[0].subtotal").value(200.0))
                .andExpect(jsonPath("$.sellers[1].providerName").value("Beta Hire"))
                .andExpect(jsonPath("$.sellers[1].subtotal").value(200.0))
                .andExpect(jsonPath("$.lines[1].providerName").value("Beta Hire"))
                .andExpect(jsonPath("$.estimatedTotal").value(400.0));
    }

    @Test
    void cartSession_returnsSessionId() throws Exception {
        mockMvc.perform(post("/api/public/cart/session")).andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").exists());
    }

    @Test
    void updateQuantity_whenMissingQuantity_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/public/cart/items/{id}/quantity", 123)
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void updateQuantity_whenZeroQuantity_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/public/cart/items/{id}/quantity", 123)
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }
}
