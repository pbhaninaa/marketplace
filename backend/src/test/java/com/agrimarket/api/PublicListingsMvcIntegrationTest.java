package com.agrimarket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.support.TestFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class PublicListingsMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void listings_returnsPagedJson() throws Exception {
        mockMvc.perform(get("/api/public/listings").param("size", "10").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listings_includesPendingProviderWithActiveSubscription() throws Exception {
        var category = fixtures.saveCategory("Transport & vehicles", "transport-vehicles");
        var provider = fixtures.savePendingProvider("Rent Merchant", "rent-merchant");
        fixtures.saveActiveSubscription(provider);
        fixtures.saveRentListing(provider, category, "Trailer", new BigDecimal("750"));

        mockMvc.perform(get("/api/public/listings").param("listingType", "RENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == 'Trailer')]").exists());
    }

    @Test
    void categories_returnsArray() throws Exception {
        mockMvc.perform(get("/api/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void setupStatus_returnsBoolean() throws Exception {
        mockMvc.perform(get("/api/public/setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsFirstAdmin").isBoolean());
    }
}
