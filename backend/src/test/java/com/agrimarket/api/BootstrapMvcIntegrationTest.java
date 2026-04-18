package com.agrimarket.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class BootstrapMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void firstAdmin_thenSecondAttempt_conflict() throws Exception {
        mockMvc.perform(get("/api/public/setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsFirstAdmin").value(true));

        String email = "platform-admin-" + UUID.randomUUID() + "@integration.test";
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", "SecurePass123"));

        mockMvc.perform(post("/api/public/first-admin").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsFirstAdmin").value(false));

        mockMvc.perform(post("/api/public/first-admin").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_EXISTS"));
    }
}
