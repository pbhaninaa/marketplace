package com.agrimarket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class PublicListingsMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listings_returnsPagedJson() throws Exception {
        mockMvc.perform(get("/api/public/listings").param("size", "10").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
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
