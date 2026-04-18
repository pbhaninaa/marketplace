package com.agrimarket.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.PasswordResetTokenRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class PublicRegistrationMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerProvider_whenNoAdmin_returns403() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@integration.test";
        String json = objectMapper.writeValueAsString(Map.of(
                "businessName", "Test Farm Co",
                "description", "Integration test provider",
                "location", "Testville",
                "ownerEmail", email,
                "password", "SecurePass123"));

        mockMvc.perform(post("/api/public/provider/register").contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_SETUP_REQUIRED"));
    }

    @Test
    void registerProvider_afterFirstAdmin_returnsCreated() throws Exception {
        String adminEmail = "platform-admin-" + UUID.randomUUID() + "@integration.test";
        mockMvc.perform(post("/api/public/first-admin")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", adminEmail, "password", "SecurePass123!"))))
                .andExpect(status().isOk());

        String email = "owner-" + UUID.randomUUID() + "@integration.test";
        String json = objectMapper.writeValueAsString(Map.of(
                "businessName", "Test Farm Co",
                "description", "Integration test provider",
                "location", "Testville",
                "ownerEmail", email,
                "password", "SecurePass123"));

        mockMvc.perform(post("/api/public/provider/register").contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void forgotPassword_thenReset_thenLogin() throws Exception {
        String email = "reset-user-" + UUID.randomUUID() + "@integration.test";
        UserAccount u = new UserAccount(email, passwordEncoder.encode("OldSecure99!"), UserRole.PLATFORM_ADMIN, null);
        userAccountRepository.save(u);

        mockMvc.perform(post("/api/public/forgot-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isNoContent());

        String token = passwordResetTokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getToken();

        mockMvc.perform(post("/api/public/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", token, "newPassword", "NewSecurePass88!"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "NewSecurePass88!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void resetPassword_badToken_returns400() throws Exception {
        mockMvc.perform(post("/api/public/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", "nope-not-a-token", "newPassword", "NewSecurePass88!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_INVALID"));
    }
}
