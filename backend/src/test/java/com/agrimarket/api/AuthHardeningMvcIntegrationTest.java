package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.PasswordResetToken;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.PasswordResetTokenRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.PasswordResetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Login abuse protection end to end: one answer for unknown email vs wrong password, account
 * lockout, password policy, hashed reset tokens and no password hashes in JSON. (Per-IP rate limits
 * are switched off in the test profile and covered by AuthRateLimitFilterTest.)
 */
class AuthHardeningMvcIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "blue-Kettle-42";

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

    private UserAccount user(UserRole role) {
        String email = "hard-" + UUID.randomUUID() + "@integration.test";
        return userAccountRepository.save(new UserAccount(email, passwordEncoder.encode(PASSWORD), role, null));
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
    }

    @Test
    void unknownEmailWrongPasswordAndDisabledAccountAllGetTheSame401() throws Exception {
        UserAccount u = user(UserRole.SUPPORT);
        UserAccount disabled = user(UserRole.SUPPORT);
        disabled.setEnabled(false);
        userAccountRepository.save(disabled);

        MvcResult wrongPassword = login(u.getEmail(), "Wrong-pass-99");
        MvcResult unknownEmail = login("nobody-" + UUID.randomUUID() + "@integration.test", "Wrong-pass-99");
        MvcResult disabledAccount = login(disabled.getEmail(), PASSWORD);

        for (MvcResult r : new MvcResult[] {wrongPassword, unknownEmail, disabledAccount}) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat(r.getResponse().getContentAsString()).contains("Invalid email or password");
        }
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenAgainstTheRightPassword() throws Exception {
        UserAccount u = user(UserRole.SUPPORT);

        for (int i = 0; i < 4; i++) {
            assertThat(login(u.getEmail(), "Wrong-pass-" + i).getResponse().getStatus()).isEqualTo(401);
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", u.getEmail(), "password", "Wrong-pass-4"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Too many failed sign-in attempts")));

        assertThat(login(u.getEmail(), PASSWORD).getResponse().getStatus()).isEqualTo(429);
        assertThat(userAccountRepository.findById(u.getId()).orElseThrow().getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void unknownEmailsLockTooSoLockoutDoesNotRevealAccounts() throws Exception {
        String ghost = "ghost-" + UUID.randomUUID() + "@integration.test";
        for (int i = 0; i < 4; i++) login(ghost, "Wrong-pass-" + i);
        assertThat(login(ghost, "Wrong-pass-4").getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCount() throws Exception {
        UserAccount u = user(UserRole.SUPPORT);

        for (int i = 0; i < 4; i++) login(u.getEmail(), "Wrong-pass-" + i);
        assertThat(login(u.getEmail(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            assertThat(login(u.getEmail(), "Wrong-pass-" + i).getResponse().getStatus()).isEqualTo(401);
        }
    }

    @Test
    void passwordResetUnlocksTheAccountAndStoresOnlyATokenHash() throws Exception {
        UserAccount u = user(UserRole.SUPPORT);
        for (int i = 0; i < 5; i++) login(u.getEmail(), "Wrong-pass-" + i);
        assertThat(login(u.getEmail(), PASSWORD).getResponse().getStatus()).isEqualTo(429);

        // The emailed link carries the raw token; only its hash is stored.
        mockMvc.perform(post("/api/public/forgot-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", u.getEmail()))))
                .andExpect(status().isNoContent());
        PasswordResetToken stored = passwordResetTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(u.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(stored.getToken()).matches("[0-9a-f]{64}");

        // Someone who reads the database cannot use the stored value as a token.
        mockMvc.perform(post("/api/public/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", stored.getToken(), "newPassword", "green-Teapot-77"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_INVALID"));

        String raw = "raw-token-" + UUID.randomUUID();
        passwordResetTokenRepository.save(new PasswordResetToken(
                PasswordResetService.hashToken(raw), u, Instant.now().plus(1, ChronoUnit.HOURS)));

        // Weak passwords are refused on reset too.
        mockMvc.perform(post("/api/public/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", raw, "newPassword", "Password123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));

        mockMvc.perform(post("/api/public/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", raw, "newPassword", "green-Teapot-77"))))
                .andExpect(status().isNoContent());

        assertThat(login(u.getEmail(), "green-Teapot-77").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void providerSignUpRejectsWeakAndCommonPasswords() throws Exception {
        user(UserRole.PLATFORM_ADMIN); // sign-up is closed until a platform admin exists
        for (String weak : new String[] {"Password123!", "qwertyuiop", "zzzzzzzzzz"}) {
            mockMvc.perform(post("/api/public/provider/register")
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "businessName", "Weak Farm",
                                    "description", "Test",
                                    "location", "Testville",
                                    "ownerEmail", "weak-" + UUID.randomUUID() + "@integration.test",
                                    "password", weak))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        }
    }

    @Test
    void responsesAndEntitiesNeverCarryThePasswordHash() throws Exception {
        UserAccount u = user(UserRole.SUPPORT);

        String loginJson = login(u.getEmail(), PASSWORD).getResponse().getContentAsString();
        assertThat(loginJson).doesNotContain(u.getPasswordHash()).doesNotContainIgnoringCase("password");

        // Defence in depth: even if an entity or principal were returned directly.
        u.setFailedLoginAttempts(2);
        u.setLockedUntil(Instant.now());
        String entityJson = objectMapper.writeValueAsString(u);
        assertThat(entityJson)
                .doesNotContain(u.getPasswordHash())
                .doesNotContain("passwordHash")
                .doesNotContain("failedLoginAttempts")
                .doesNotContain("lockedUntil");
        String principalJson = objectMapper.writeValueAsString(new MarketUserPrincipal(u));
        assertThat(principalJson).doesNotContain(u.getPasswordHash()).doesNotContainIgnoringCase("password");
    }
}
