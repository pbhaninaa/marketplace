package com.agrimarket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class ProviderListingsMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String token;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Provider p = fixtures.saveActiveProvider("Listings Co", "listings-" + u);
        fixtures.saveActiveSubscription(p);
        Category c = fixtures.saveCategory("Seeds", "seeds-" + u);
        fixtures.saveSaleListing(p, c, "Maize seed", new BigDecimal("10.00"), 5);

        UserAccount owner = new UserAccount(
                "listings-owner-" + u + "@integration.test",
                passwordEncoder.encode("irrelevant"),
                UserRole.PROVIDER_OWNER,
                p);
        userAccountRepository.save(owner);
        userAccountRepository.flush();
        token = jwtService.createToken(owner.getId(), owner.getEmail(), owner.getRole(), p.getId());
    }

    @Test
    void providerListings_returnsPagedContent() throws Exception {
        mockMvc.perform(get("/api/provider/me/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}

