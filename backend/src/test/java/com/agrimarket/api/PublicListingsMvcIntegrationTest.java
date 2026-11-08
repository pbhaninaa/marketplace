package com.agrimarket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    void listings_hidesProvidersThatCannotTakeOrders() throws Exception {
        var category = fixtures.saveCategory("Transport & vehicles", "transport-vehicles");
        var pending = fixtures.savePendingProvider("Rent Merchant", "rent-merchant");
        fixtures.saveRentListing(pending, category, "Pending Trailer", new BigDecimal("750"));
        var lapsed = fixtures.saveActiveProviderWithTrialEndingAt(
                "Lapsed Farm", "lapsed-farm", Instant.now().minus(1, ChronoUnit.DAYS));
        fixtures.saveRentListing(lapsed, category, "Lapsed Trailer", new BigDecimal("750"));

        mockMvc.perform(get("/api/public/listings").param("listingType", "RENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == 'Pending Trailer')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.title == 'Lapsed Trailer')]").doesNotExist());
        mockMvc.perform(get("/api/public/provider-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Lapsed Farm')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.name == 'Rent Merchant')]").doesNotExist());
    }

    @Test
    void listings_showsProvidersOnTrialOrWithPaidSubscription() throws Exception {
        var category = fixtures.saveCategory("Equipment", "equipment");
        var onTrial = fixtures.saveActiveProviderWithTrialEndingAt(
                "Trial Farm", "trial-farm", Instant.now().plus(5, ChronoUnit.DAYS));
        fixtures.saveRentListing(onTrial, category, "Trial Tractor", new BigDecimal("900"));
        var paid = fixtures.saveActiveProviderWithTrialEndingAt(
                "Paid Farm", "paid-farm", Instant.now().minus(30, ChronoUnit.DAYS));
        fixtures.saveActiveSubscription(paid);
        fixtures.saveRentListing(paid, category, "Paid Tractor", new BigDecimal("900"));

        mockMvc.perform(get("/api/public/listings").param("listingType", "RENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == 'Trial Tractor')]").exists())
                .andExpect(jsonPath("$.content[?(@.title == 'Paid Tractor')]").exists());
        mockMvc.perform(get("/api/public/provider-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Trial Farm')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Paid Farm')]").exists());
    }

    @Test
    void listings_ignoresZeroMinPriceForRent() throws Exception {
        var category = fixtures.saveCategory("Transport", "transport");
        var provider = fixtures.saveActiveProviderWithTrialEndingAt(
                "Rent Co", "rent-co", Instant.now().plus(5, ChronoUnit.DAYS));
        fixtures.saveRentListing(provider, category, "Semi Trailer", new BigDecimal("750"));

        mockMvc.perform(get("/api/public/listings").param("listingType", "RENT").param("minPrice", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == 'Semi Trailer')]").exists());
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
