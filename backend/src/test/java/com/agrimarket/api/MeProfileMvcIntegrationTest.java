package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class MeProfileMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void getProfile_returnsOwnDetailsIncludingProviderName() throws Exception {
        var provider = fixtures.saveActiveProvider("Profile Farm", "profile-farm-" + UUID.randomUUID());
        UserAccount owner = new UserAccount(
                "owner-profile-" + UUID.randomUUID() + "@integration.test",
                passwordEncoder.encode("irrelevant"),
                UserRole.PROVIDER_OWNER,
                provider);
        owner.setFirstName("Thandi");
        owner.setPhoneNumber("+27 82 000 0000");
        userAccountRepository.saveAndFlush(owner);

        String token = jwtService.createToken(owner.getId(), owner.getEmail(), UserRole.PROVIDER_OWNER, provider.getId());

        mockMvc.perform(get("/api/me/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.role").value("PROVIDER_OWNER"))
                .andExpect(jsonPath("$.firstName").value("Thandi"))
                .andExpect(jsonPath("$.phoneNumber").value("+27 82 000 0000"))
                .andExpect(jsonPath("$.providerName").value("Profile Farm"));
    }

    @Test
    void updateProfile_trimsValuesAndClearsBlanks() throws Exception {
        UserAccount admin = new UserAccount(
                "admin-profile-" + UUID.randomUUID() + "@integration.test",
                passwordEncoder.encode("irrelevant"),
                UserRole.PLATFORM_ADMIN,
                null);
        admin.setLastName("Old");
        userAccountRepository.saveAndFlush(admin);
        String token = jwtService.createToken(admin.getId(), admin.getEmail(), UserRole.PLATFORM_ADMIN, null);

        mockMvc.perform(put("/api/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"  Sipho \",\"lastName\":\"  \",\"displayName\":\"Sipho M\",\"phoneNumber\":\"082 123 4567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Sipho"))
                .andExpect(jsonPath("$.lastName").doesNotExist())
                .andExpect(jsonPath("$.displayName").value("Sipho M"));

        UserAccount saved = userAccountRepository.findById(admin.getId()).orElseThrow();
        assertThat(saved.getFirstName()).isEqualTo("Sipho");
        assertThat(saved.getLastName()).isNull();
        assertThat(saved.getPhoneNumber()).isEqualTo("082 123 4567");
    }

    @Test
    void updateProfile_rejectsInvalidPhone() throws Exception {
        UserAccount admin = new UserAccount(
                "admin-phone-" + UUID.randomUUID() + "@integration.test",
                passwordEncoder.encode("irrelevant"),
                UserRole.PLATFORM_ADMIN,
                null);
        userAccountRepository.saveAndFlush(admin);
        String token = jwtService.createToken(admin.getId(), admin.getEmail(), UserRole.PLATFORM_ADMIN, null);

        mockMvc.perform(put("/api/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"call me maybe\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProfile_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/profile")).andExpect(status().is4xxClientError());
    }
}
