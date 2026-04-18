package com.agrimarket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class ProviderUploadMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ProviderRepository providerRepository;

    @AfterEach
    void cleanup() throws Exception {
        // no-op (DB-backed images)
    }

    @Test
    void providerUploadImage_withJwt_returnsUrls() throws Exception {
        Provider p = new Provider("Upload Provider", "upload-provider", "Test", "Cape Town");
        p.setStatus(ProviderStatus.ACTIVE);
        p = providerRepository.save(p);

        String email = "provider-upload-" + UUID.randomUUID() + "@integration.test";
        UserAccount u = new UserAccount(email, passwordEncoder.encode("irrelevant"), UserRole.PROVIDER_OWNER, p);
        userAccountRepository.save(u);
        userAccountRepository.flush();

        String token = jwtService.createToken(u.getId(), u.getEmail(), u.getRole(), p.getId());

        MockMultipartFile img = new MockMultipartFile(
                "files",
                "photo.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}); // minimal bytes; controller only checks content-type

        mockMvc.perform(multipart("/api/provider/me/uploads/images")
                        .file(img)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls").isArray())
                .andExpect(jsonPath("$.urls[0]").value(org.hamcrest.Matchers.startsWith("/api/public/images/")));
    }
}

