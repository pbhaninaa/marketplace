package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.*;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.OrderRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for Provider Order CRUD operations.
 * Tests the new ProviderOrderController endpoints and payment status constraints.
 */
@DisplayName("Provider Order CRUD Integration Tests")
class ProviderOrderCrudIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String sessionId;
    private Listing listing;
    private String providerToken;
    private Provider provider;
    private Long orderId;

    @BeforeEach
    void setup() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        sessionId = "order-crud-session-" + u;

        Category cat = fixtures.saveCategory("Vegetables", "veg-" + u);
        provider = fixtures.saveActiveProvider("Fresh Farm", "fresh-farm-" + u);
        fixtures.saveActiveSubscription(provider);
        listing = fixtures.saveSaleListing(provider, cat, "Tomatoes", new BigDecimal("15"), 10);

        UserAccount owner = new UserAccount(
                "owner-" + u + "@test.com",
                passwordEncoder.encode("Password123!"),
                UserRole.PROVIDER_OWNER,
                provider);
        userAccountRepository.save(owner);
        providerToken = jwtService.createToken(owner.getId(), owner.getEmail(), owner.getRole(), provider.getId());

        // Create an order via checkout
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("listingId", listing.getId(), "quantity", 2))))
                .andExpect(status().isOk());

        MvcResult checkoutResult = mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "guestName", "John Doe",
                                "guestEmail", "john+" + u + "@test.com",
                                "guestPhone", "0821234567",
                                "deliveryOrPickup", "Pickup",
                                "paymentMethod", "Cash"))))
                .andExpect(status().isOk())
                .andReturn();

        String checkoutBody = checkoutResult.getResponse().getContentAsString();
        JsonNode checkoutJson = objectMapper.readTree(checkoutBody);
        orderId = checkoutJson.get("OrderIds").get(0).asLong();
    }

    @Test
    @DisplayName("GET /api/provider/orders - Should return provider's orders")
    void shouldGetProviderOrders() throws Exception {
        mockMvc.perform(get("/api/provider/orders")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(orderId))
                .andExpect(jsonPath("$.content[0].guestName").value("John Doe"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].items").isArray())
                .andExpect(jsonPath("$.content[0].items[0].listingTitle").value("Tomatoes"))
                .andExpect(jsonPath("$.content[0].items[0].quantity").value(2));
    }

    @Test
    @DisplayName("GET /api/provider/orders/{orderId} - Should return order with items")
    void shouldGetOrderById() throws Exception {
        mockMvc.perform(get("/api/provider/orders/" + orderId)
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.guestName").value("John Doe"))
                .andExpect(jsonPath("$.guestEmail").exists())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    @DisplayName("PUT /api/provider/orders/{orderId}/status - Should update order status")
    void shouldUpdateOrderStatus() throws Exception {
        mockMvc.perform(put("/api/provider/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PAID"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // Verify inventory was deducted
        Listing updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertThat(updatedListing.getStockReserved()).isEqualTo(0);
    }

    @Test
    @DisplayName("DELETE /api/provider/orders/{orderId}/cancel - Should cancel PENDING order")
    void shouldCancelPendingOrder() throws Exception {
        mockMvc.perform(delete("/api/provider/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Order cancelled successfully"));

        // Verify order is cancelled
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("DELETE /api/provider/orders/{orderId}/cancel - Should NOT cancel PAID order")
    void shouldNotCancelPaidOrder() throws Exception {
        // First update to PAID
        mockMvc.perform(put("/api/provider/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PAID"))))
                .andExpect(status().isOk());

        // Try to cancel - should fail
        mockMvc.perform(delete("/api/provider/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_CANCEL_PAID"));
    }

    @Test
    @DisplayName("DELETE /api/provider/orders/{orderId} - Should delete PENDING order")
    void shouldDeletePendingOrder() throws Exception {
        mockMvc.perform(delete("/api/provider/orders/" + orderId)
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Order deleted successfully"));

        // Verify order is deleted
        assertThat(orderRepository.findById(orderId)).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/provider/orders/{orderId} - Should NOT delete PAID order")
    void shouldNotDeletePaidOrder() throws Exception {
        // First update to PAID
        mockMvc.perform(put("/api/provider/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PAID"))))
                .andExpect(status().isOk());

        // Try to delete - should fail
        mockMvc.perform(delete("/api/provider/orders/" + orderId)
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_DELETE_PAID"));
    }

    @Test
    @DisplayName("Payment status should be PENDING initially and PAID after confirmation")
    void shouldTrackPaymentStatus() throws Exception {
        // Check initial status
        MvcResult result1 = mockMvc.perform(get("/api/provider/orders/" + orderId)
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json1 = objectMapper.readTree(result1.getResponse().getContentAsString());
        assertThat(json1.get("paymentStatus").asText()).isEqualTo("PENDING");

        // Update to PAID
        mockMvc.perform(put("/api/provider/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + providerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PAID"))))
                .andExpect(status().isOk());

        // Verify payment status updated
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }
}
