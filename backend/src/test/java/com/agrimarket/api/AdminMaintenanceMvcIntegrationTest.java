package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class AdminMaintenanceMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void cleanDb_keepsAdminAndRemovesMarketplaceData() throws Exception {
        String adminEmail = "admin-clean-" + UUID.randomUUID() + "@integration.test";
        UserAccount admin =
                new UserAccount(adminEmail, passwordEncoder.encode("irrelevant"), UserRole.PLATFORM_ADMIN, null);
        userAccountRepository.saveAndFlush(admin);

        var category = fixtures.saveCategory("Transport", "transport-clean-" + UUID.randomUUID());
        var provider = fixtures.saveActiveProvider("Clean Provider", "clean-provider-" + UUID.randomUUID());
        fixtures.saveActiveSubscription(provider);
        fixtures.saveRentListing(provider, category, "Trailer to wipe", new BigDecimal("750"));

        UserAccount providerOwner = new UserAccount(
                "owner-clean-" + UUID.randomUUID() + "@integration.test",
                passwordEncoder.encode("irrelevant"),
                UserRole.PROVIDER_OWNER,
                provider);
        userAccountRepository.saveAndFlush(providerOwner);

        assertThat(providerRepository.count()).isGreaterThan(0);
        assertThat(listingRepository.count()).isGreaterThan(0);

        String token = jwtService.createToken(admin.getId(), admin.getEmail(), UserRole.PLATFORM_ADMIN, null);

        mockMvc.perform(post("/api/admin/maintenance/clean-db")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keptAdminUserId").value(admin.getId().intValue()))
                .andExpect(jsonPath("$.providers").exists())
                .andExpect(jsonPath("$.order_items").exists())
                .andExpect(jsonPath("$.orders").exists());

        assertThat(userAccountRepository.findById(admin.getId())).isPresent();
        assertThat(userAccountRepository.count()).isEqualTo(1);
        assertThat(providerRepository.count()).isZero();
        assertThat(listingRepository.count()).isZero();
    }
}
