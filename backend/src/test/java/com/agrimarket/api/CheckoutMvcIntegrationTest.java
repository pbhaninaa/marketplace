package com.agrimarket.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.Provider;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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

    @Autowired
    private ProviderRepository providerRepository;

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
        addItemToCart();

        Map<String, Object> checkoutBody = Map.of(
                "guestName", "Test Guest",
                "guestEmail", "guest+" + UUID.randomUUID() + "@checkout.test",
                "guestPhone", "0820000000",
                "deliveryOrPickup", "Collect at gate 3",
                "paymentMethod", "CASH");

        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseOrderIds").isArray())
                .andExpect(jsonPath("$.providerId").value(listing.getProvider().getId().intValue()));
    }

    @Test
    void guestCheckout_rejectsManualEftWithoutBankDetails() throws Exception {
        addItemToCart();
        Map<String, Object> body = checkoutBody("EFT");

        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BANK_DETAILS"));
    }

    @Test
    void guestCheckout_acceptsManualEftWhenProviderHasBankDetails() throws Exception {
        Provider provider = listing.getProvider();
        provider.setAcceptedPaymentMethods(EnumSet.of(PaymentMethod.EFT, PaymentMethod.CASH));
        provider.setBankName("Test Bank");
        provider.setBankAccountName("Checkout Co");
        provider.setBankAccountNumber("1234567890");
        provider.setBankBranchCode("250655");
        providerRepository.save(provider);

        addItemToCart();
        Map<String, Object> body = checkoutBody("EFT");

        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseOrderIds").isArray())
                .andExpect(jsonPath("$.providerId").value(listing.getProvider().getId().intValue()));
    }

    @Test
    void guestCheckout_rejectsPeachSubtypeForCash() throws Exception {
        addItemToCart();
        Map<String, Object> body = checkoutBody("CASH");
        body.put("peachPaymentMethod", "CARD");

        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PEACH_PAYMENT_METHOD"));
    }

    @Test
    void multiSellerCart_isCheckedOutOneSellerAtATime() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Category cat = fixtures.saveCategory("Feed", "feed-" + u);
        Provider second = fixtures.saveActiveProvider("Second Farm", "second-" + u);
        fixtures.saveActiveSubscription(second);
        Listing feed = fixtures.saveSaleListing(second, cat, "Lucerne", new BigDecimal("80"), 10);

        addItemToCart();
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", feed.getId(), "quantity", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellers.length()").value(2));

        // Two sellers and no choice: the buyer must pick which seller to pay first.
        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutBody("CASH"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHOOSE_SELLER"));

        // Check out the second seller only.
        Map<String, Object> body = checkoutBody("CASH");
        body.put("providerId", second.getId());
        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(second.getId().intValue()))
                .andExpect(jsonPath("$.providerName").value("Second Farm"))
                .andExpect(jsonPath("$.purchaseOrderIds.length()").value(1))
                .andExpect(jsonPath("$.sellersRemaining").value(1));

        // The first seller's items are still in the cart, ready for their own checkout.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/public/cart")
                        .header("X-Session-Id", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellers.length()").value(1))
                .andExpect(jsonPath("$.sellers[0].providerId").value(listing.getProvider().getId().intValue()))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].title").value("Bale of hay"));

        // With one seller left, providerId is optional again; the cart ends empty.
        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutBody("CASH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(listing.getProvider().getId().intValue()))
                .andExpect(jsonPath("$.sellersRemaining").value(0));
    }

    @Test
    void checkout_rejectsASellerThatIsNotInTheCart() throws Exception {
        addItemToCart();
        Map<String, Object> body = checkoutBody("CASH");
        body.put("providerId", 999999);
        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_NOT_IN_CART"));
    }

    private void addItemToCart() throws Exception {
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("listingId", listing.getId(), "quantity", 2))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> checkoutBody(String paymentMethod) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guestName", "Test Guest");
        body.put("guestEmail", "guest+" + UUID.randomUUID() + "@checkout.test");
        body.put("guestPhone", "0820000000");
        body.put("deliveryOrPickup", "Collect at gate 3");
        body.put("paymentMethod", paymentMethod);
        return body;
    }
}
