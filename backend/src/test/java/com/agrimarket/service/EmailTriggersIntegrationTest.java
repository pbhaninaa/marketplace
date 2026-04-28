package com.agrimarket.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("Email triggers integration")
class EmailTriggersIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;

    @MockBean private EmailService emailService;

    private String sessionId;

    @BeforeEach
    void setup() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        sessionId = "email-trigger-session-" + u;

        var cat = fixtures.saveCategory("Veg", "veg-" + u);
        var provider = fixtures.saveActiveProvider("Farm", "farm-" + u);
        fixtures.saveActiveSubscription(provider);
        var listing = fixtures.saveSaleListing(provider, cat, "Tomatoes", new BigDecimal("10"), 10);

        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("listingId", listing.getId(), "quantity", 1))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("checkout triggers email sends (best-effort)")
    void checkoutTriggersEmails() throws Exception {
        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "guestName", "Test",
                                "guestEmail", "client@example.test",
                                "guestPhone", "0820000000",
                                "deliveryOrPickup", "Pickup",
                                "paymentMethod", "Cash"))))
                .andExpect(status().isOk());

        verify(emailService, atLeastOnce()).send(any(), any(), any(), any());
    }
}

