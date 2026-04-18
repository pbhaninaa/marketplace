package com.agrimarket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class AdminSecurityMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void adminProviders_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/providers")).andExpect(status().isForbidden());
    }

    @Test
    void adminProviders_withPlatformAdminJwt_returns200() throws Exception {
        String email = "admin-jwt-" + UUID.randomUUID() + "@integration.test";
        UserAccount admin = new UserAccount(email, passwordEncoder.encode("irrelevant"), UserRole.PLATFORM_ADMIN, null);
        userAccountRepository.save(admin);
        userAccountRepository.flush();

        String token = jwtService.createToken(admin.getId(), admin.getEmail(), UserRole.PLATFORM_ADMIN, null);

        mockMvc.perform(get("/api/admin/providers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void providerStaff_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/provider/me/staff")).andExpect(status().isForbidden());
    }

    @Test
    void login_withUnknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@void.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
