package com.agrimarket.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.CategoryRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class ProviderBankingCheckoutMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void cartResponse_includesProviderBankingDetailsWhenLocked() throws Exception {
        // Provider + owner
        Provider p = new Provider("Banked Provider", "banked-provider", "Test", "Joburg");
        p.setStatus(ProviderStatus.ACTIVE);
        p.setBankName("Test Bank");
        p.setBankAccountName("Banked Provider PTY");
        p.setBankAccountNumber("1234567890");
        p.setBankBranchCode("250655");
        p.setBankReference("Use email");
        p.setAcceptedPaymentMethods(EnumSet.of(PaymentMethod.EFT));
        p = providerRepository.save(p);
        fixtures.saveActiveSubscription(p);

        UserAccount owner = new UserAccount("owner-banking@integration.test", passwordEncoder.encode("SecurePass123!"), UserRole.PROVIDER_OWNER, p);
        userAccountRepository.save(owner);

        String token = jwtService.createToken(owner.getId(), owner.getEmail(), owner.getRole(), p.getId());

        // Update settings via API (ensures endpoint works)
        mockMvc.perform(patch("/api/provider/me/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "location", "Joburg",
                                "bankName", "Test Bank",
                                "bankAccountName", "Banked Provider PTY",
                                "bankAccountNumber", "1234567890",
                                "bankBranchCode", "250655",
                                "bankReference", "Use email",
                                "acceptedPaymentMethods", new String[] {"EFT"}
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("Test Bank"));

        // Listing
        var c = categoryRepository.save(new com.agrimarket.domain.Category("Cat", "cat"));
        var l = new com.agrimarket.domain.Listing();
        l.setProvider(p);
        l.setCategory(c);
        l.setListingType(com.agrimarket.domain.ListingType.SALE);
        l.setTitle("Item 1");
        l.setDescription("Desc");
        l.setUnitPrice(new BigDecimal("10.00"));
        l.setStockQuantity(10);
        l.setActive(true);
        l = listingRepository.save(l);
        final Long listingId = l.getId();

        // Create cart session and add item
        String sessionKey = mockMvc.perform(post("/api/public/cart/session"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String sessionId = objectMapper.readTree(sessionKey).get("sessionId").asText();

        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {
                            {
                                put("listingId", listingId);
                                put("quantity", 1);
                                put("rentalStart", null);
                                put("rentalEnd", null);
                            }
                        })))
                .andExpect(status().isOk());

        // Cart response should include banking details
        mockMvc.perform(get("/api/public/cart").header("X-Session-Id", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedProviderId").value(p.getId()))
                .andExpect(jsonPath("$.lockedProviderBankName").value("Test Bank"))
                .andExpect(jsonPath("$.lockedProviderBankAccountNumber").value("1234567890"))
                .andExpect(jsonPath("$.lockedProviderAcceptedPaymentMethods[0]").value("EFT"));
    }
}

