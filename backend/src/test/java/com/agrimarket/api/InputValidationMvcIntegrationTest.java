package com.agrimarket.api;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** API-side input validation: bad formats are rejected with a 400 VALIDATION and a field message. */
class InputValidationMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private String sessionId;
    private Listing listing;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        sessionId = "validation-session-" + u;
        Category cat = fixtures.saveCategory("Feed", "feed-" + u);
        Provider p = fixtures.saveActiveProvider("Validation Farm", "validation-farm-" + u);
        fixtures.saveActiveSubscription(p);
        listing = fixtures.saveSaleListing(p, cat, "Lucerne bale", new BigDecimal("80"), 50);
    }

    @Test
    void cartAdd_rejectsZeroQuantity() throws Exception {
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("listingId", listing.getId(), "quantity", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message", containsString("quantity")));
    }

    @Test
    void checkout_rejectsBadPhone() throws Exception {
        addItemToCart();
        Map<String, Object> body = checkoutBody();
        body.put("guestPhone", "12345");

        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message", containsString("guestPhone")))
                .andExpect(jsonPath("$.message", containsString("country code plus 9 digits")));
    }

    @Test
    void checkout_rejectsBadEmailAndName() throws Exception {
        addItemToCart();
        Map<String, Object> badEmail = checkoutBody();
        badEmail.put("guestEmail", "guest@nowhere");
        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(json(badEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("guestEmail")));

        Map<String, Object> badName = checkoutBody();
        badName.put("guestName", "<script>alert(1)</script>");
        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(json(badName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("guestName")));
    }

    @Test
    void checkout_storesLocalPhoneInInternationalForm() throws Exception {
        addItemToCart();
        Map<String, Object> body = checkoutBody();
        body.put("guestPhone", "082 123 4567");

        String res = mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode ids = objectMapper.readTree(res).get("purchaseOrderIds");
        Order order = orderRepository.findById(ids.get(0).asLong()).orElseThrow();
        assertEquals("+27821234567", order.getGuestPhone());
    }

    @Test
    void providerRegistration_rejectsBadOwnerEmail() throws Exception {
        mockMvc.perform(post("/api/public/provider/register")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "businessName", "Test Farm Co",
                                "description", "Integration test provider",
                                "location", "Testville",
                                "ownerEmail", "owner@localhost",
                                "password", "SecurePass123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message", containsString("ownerEmail")));
    }

    @Test
    void providerSettings_rejectsBadBankDetails_andStoresDigitsOnly() throws Exception {
        Provider p = listing.getProvider();
        UserAccount owner = userAccountRepository.save(new UserAccount(
                "owner-" + UUID.randomUUID() + "@integration.test",
                passwordEncoder.encode("SecurePass123!"),
                UserRole.PROVIDER_OWNER,
                p));
        String token = jwtService.createToken(owner.getId(), owner.getEmail(), owner.getRole(), p.getId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("location", "Joburg");
        body.put("bankName", "Test Bank");
        body.put("bankAccountName", "Validation Farm");
        body.put("bankAccountNumber", "1234567890");
        body.put("bankBranchCode", "25");
        body.put("acceptedPaymentMethods", new String[] {"CASH"});

        mockMvc.perform(patch("/api/provider/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bankBranchCode")));

        body.put("bankBranchCode", "250655");
        body.put("bankAccountNumber", "12AB567890");
        mockMvc.perform(patch("/api/provider/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bankAccountNumber")));

        body.put("bankAccountNumber", "1234 5678 90");
        mockMvc.perform(patch("/api/provider/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
        assertEquals("1234567890", providerRepository.findById(p.getId()).orElseThrow().getBankAccountNumber());
    }

    @Test
    void clientOtp_rejectsTargetThatIsNeitherEmailNorPhone() throws Exception {
        mockMvc.perform(post("/api/public/client/otp/request")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("target", "not-a-target"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    private void addItemToCart() throws Exception {
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("listingId", listing.getId(), "quantity", 1))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> checkoutBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guestName", "Thandi Mokoena");
        body.put("guestEmail", "guest+" + UUID.randomUUID() + "@checkout.test");
        body.put("guestPhone", "+27 82 123 4567");
        body.put("deliveryOrPickup", "Collect at gate 3");
        body.put("paymentMethod", "CASH");
        return body;
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
