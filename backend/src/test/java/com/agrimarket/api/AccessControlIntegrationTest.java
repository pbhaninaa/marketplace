package com.agrimarket.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.ProviderStaffPermission;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.ProviderStaffPermissionRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression tests for tenant isolation and in-tenant role/permission checks: a provider must never read or
 * change another provider's orders, staff without the right permission keys (or the read-only viewer role)
 * cannot change orders / listings / settings, and guest invoices cannot be fetched by a guessable order number.
 */
class AccessControlIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ProviderStaffPermissionRepository permissionRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private Provider providerA;
    private Provider providerB;
    private Listing listingA;
    private String ownerAToken;
    private String ownerBToken;
    private UserAccount ownerB;
    private Long orderId;
    private String verificationCode;
    private String guestEmail;

    @BeforeEach
    void seed() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Category cat = fixtures.saveCategory("Produce", "produce-" + u);
        providerA = fixtures.saveActiveProvider("Farm A", "farm-a-" + u);
        providerB = fixtures.saveActiveProvider("Farm B", "farm-b-" + u);
        fixtures.saveActiveSubscription(providerA);
        fixtures.saveActiveSubscription(providerB);
        listingA = fixtures.saveSaleListing(providerA, cat, "Maize", new BigDecimal("20"), 50);

        ownerAToken = tokenFor(user("owner-a-" + u, UserRole.PROVIDER_OWNER, providerA));
        ownerB = user("owner-b-" + u, UserRole.PROVIDER_OWNER, providerB);
        ownerBToken = tokenFor(ownerB);

        String session = "acl-" + u;
        guestEmail = "guest-" + u + "@acl.test";
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", session)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("listingId", listingA.getId(), "quantity", 1))))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", session)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "guestName", "Guest",
                                "guestEmail", guestEmail,
                                "guestPhone", "0820000000",
                                "deliveryOrPickup", "Pickup",
                                "paymentMethod", "CASH"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        orderId = json.get("purchaseOrderIds").get(0).asLong();
        verificationCode = json.get("verificationCodes").get(0).asText();
    }

    // ------------------------------------------------------------------ cross-tenant orders (404, not 403)

    @Test
    void ownerOfOtherProvider_cannotReadOrDownloadOrder() throws Exception {
        mockMvc.perform(get("/api/provider/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/provider/me/orders/purchases/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/provider/me/orders/purchases/" + orderId + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/provider/me/orders/purchases/" + orderId + "/invoice")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken)))
                .andExpect(status().isNotFound());

        // Sanity: the owning provider still sees it.
        mockMvc.perform(get("/api/provider/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken)))
                .andExpect(status().isOk());
    }

    @Test
    void ownerOfOtherProvider_cannotChangeVerifyOrDeleteOrder() throws Exception {
        mockMvc.perform(put("/api/provider/orders/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/provider/me/verify/order/" + verificationCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/provider/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/provider/me/orders/purchases").param("ids", orderId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerBToken)))
                .andExpect(status().isNotFound());

        Order unchanged = orderRepository.findById(orderId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unchanged.getVerifiedAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(unchanged.getStatus().name()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void payrollAttribution_cannotPointAtAnotherProvidersUser() throws Exception {
        mockMvc.perform(put("/api/provider/orders/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("status", "CANCELLED", "completedByStaffId", ownerB.getId()))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ in-tenant permissions

    @Test
    void staffWithoutOrderPermissions_cannotReadOrders() throws Exception {
        String token = tokenFor(staff(UserRole.PROVIDER_STAFF, ProviderPermissionKey.LISTINGS_READ));
        mockMvc.perform(get("/api/provider/orders").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/provider/me/orders/purchases/" + orderId + "/invoice")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewer_canReadButNotChangeOrders() throws Exception {
        String token = tokenFor(staff(UserRole.PROVIDER_VIEWER, ProviderPermissionKey.ORDERS_READ, ProviderPermissionKey.ORDERS_WRITE));
        mockMvc.perform(get("/api/provider/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/provider/orders/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/provider/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffWithOrderWrite_cannotWipeAllOrders() throws Exception {
        String token = tokenFor(staff(UserRole.PROVIDER_STAFF, ProviderPermissionKey.ORDERS_READ, ProviderPermissionKey.ORDERS_WRITE));
        mockMvc.perform(delete("/api/provider/me/orders/purchases").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/provider/orders/bulk").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(orderRepository.findById(orderId)).isPresent();
    }

    @Test
    void staffWithoutListingWrite_cannotDeleteOrUpload() throws Exception {
        String token = tokenFor(staff(UserRole.PROVIDER_STAFF, ProviderPermissionKey.LISTINGS_READ));
        mockMvc.perform(delete("/api/provider/me/listings/" + listingA.getId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/provider/me/uploads/images")
                        .file(new MockMultipartFile("files", "a.png", "image/png", new byte[] {1, 2, 3}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        // Listing reads stay open to every provider user.
        mockMvc.perform(get("/api/provider/me/listings").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void viewerWithListingWriteKey_stillCannotDeleteListing() throws Exception {
        String token = tokenFor(staff(UserRole.PROVIDER_VIEWER, ProviderPermissionKey.LISTINGS_WRITE));
        mockMvc.perform(delete("/api/provider/me/listings/" + listingA.getId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_cannotChangeProviderSettingsOrBankDetails() throws Exception {
        String token = tokenFor(staff(UserRole.PROVIDER_STAFF, ProviderPermissionKey.values()));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("location", "Elsewhere");
        settings.put("bankName", "Attacker Bank");
        settings.put("bankAccountName", "Attacker");
        settings.put("bankAccountNumber", "999999");
        settings.put("bankBranchCode", "000");
        settings.put("deliveryAvailable", false);
        mockMvc.perform(patch("/api/provider/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settings)))
                .andExpect(status().isForbidden());
        // Owner can still update settings.
        mockMvc.perform(patch("/api/provider/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerAToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("location", "Farmville", "deliveryAvailable", false, "acceptedPaymentMethods", java.util.List.of("CASH")))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ guest invoice IDOR

    @Test
    void guestInvoice_friendlyOrderNumberWithoutEmail_isRejected() throws Exception {
        Order o = orderRepository.findById(orderId).orElseThrow();
        String year = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneId.systemDefault()).format(o.getCreatedAt());
        String friendly = "ORD-" + year + "-" + String.format("%06d", orderId);

        mockMvc.perform(get("/api/public/order-invoice").param("orderRef", friendly))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/order-invoice").param("orderRef", orderId.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/order-invoice").param("orderRef", friendly).param("email", "someone@else.test"))
                .andExpect(status().isNotFound());
        // Unknown order numbers look the same as a wrong email.
        mockMvc.perform(get("/api/public/order-invoice").param("orderRef", "99999999").param("email", "someone@else.test"))
                .andExpect(status().isNotFound());
    }

    @Test
    void guestInvoice_ownerOfOrderCanDownload() throws Exception {
        mockMvc.perform(get("/api/public/order-invoice").param("orderRef", orderId.toString()).param("email", guestEmail))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/order-invoice").param("orderRef", verificationCode))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/order-invoice").param("orderRef", verificationCode).param("email", "wrong@acl.test"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    private UserAccount user(String prefix, UserRole role, Provider provider) {
        UserAccount account = new UserAccount(prefix + "@acl.test", passwordEncoder.encode("Password123!"), role, provider);
        return userAccountRepository.save(account);
    }

    private UserAccount staff(UserRole role, ProviderPermissionKey... keys) {
        UserAccount account = user(role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8), role, providerA);
        for (ProviderPermissionKey key : keys) {
            permissionRepository.save(new ProviderStaffPermission(providerA, account, key));
        }
        return account;
    }

    private String tokenFor(UserAccount account) {
        Long pid = account.getProvider() != null ? account.getProvider().getId() : null;
        return jwtService.createToken(account.getId(), account.getEmail(), account.getRole(), pid);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
