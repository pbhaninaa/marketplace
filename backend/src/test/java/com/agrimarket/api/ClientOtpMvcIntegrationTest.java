package com.agrimarket.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.repo.ClientOtpChallengeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class ClientOtpMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientOtpChallengeRepository clientOtpChallengeRepository;

    @Test
    void requestOtp_returns204_andStoresChallenge() throws Exception {
        String target = "client@example.test";
        mockMvc.perform(post("/api/public/client/otp/request")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("target", target))))
                .andExpect(status().isNoContent());

        var row = clientOtpChallengeRepository.findTopByTargetOrderByCreatedAtDesc(target).orElseThrow();
        // hash exists and expires in future
        org.junit.jupiter.api.Assertions.assertNotNull(row.getCodeHash());
        org.junit.jupiter.api.Assertions.assertTrue(row.getExpiresAt().isAfter(java.time.Instant.now()));
    }

    @Test
    void verifyOtp_withWrongCode_returns400() throws Exception {
        String target = "client2@example.test";
        mockMvc.perform(post("/api/public/client/otp/request")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("target", target))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/public/client/otp/verify")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("target", target, "code", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP"));
    }
}

