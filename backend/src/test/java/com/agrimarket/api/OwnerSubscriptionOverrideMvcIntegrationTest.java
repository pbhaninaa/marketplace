package com.agrimarket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.agrimarket.service.ProviderTrialSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Only the platform owner (first admin) may override; other admins get 403; provider sees the result. */
class OwnerSubscriptionOverrideMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private JwtService jwtService;

    private String ownerToken;
    private String otherAdminToken;
    private String providerToken;
    private Provider provider;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount owner = new UserAccount("founder-" + suffix + "@admin.test", "x", UserRole.PLATFORM_ADMIN, null);
        owner.setCreatedAt(Instant.EPOCH); // earliest admin => platform owner
        userAccountRepository.save(owner);
        UserAccount other = userAccountRepository.save(
                new UserAccount("ops-" + suffix + "@admin.test", "x", UserRole.PLATFORM_ADMIN, null));

        provider = new Provider("Mvc Farm " + suffix, "mvc-farm-" + suffix, "desc", "loc");
        provider.setStatus(ProviderStatus.ACTIVE);
        ProviderTrialSupport.assignTrialOnce(provider, Instant.now().minus(ProviderTrialSupport.TRIAL_DAYS - 5, ChronoUnit.DAYS));
        providerRepository.save(provider);
        UserAccount providerUser = userAccountRepository.save(
                new UserAccount("farmer-" + suffix + "@farm.test", "x", UserRole.PROVIDER_OWNER, provider));
        userAccountRepository.flush();

        ownerToken = jwtService.createToken(owner.getId(), owner.getEmail(), UserRole.PLATFORM_ADMIN, null);
        otherAdminToken = jwtService.createToken(other.getId(), other.getEmail(), UserRole.PLATFORM_ADMIN, null);
        providerToken = jwtService.createToken(
                providerUser.getId(), providerUser.getEmail(), UserRole.PROVIDER_OWNER, provider.getId());
    }

    private String base() {
        return "/api/admin/providers/" + provider.getId() + "/subscription";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void anyAdminCanRead_onlyOwnerFlagged() throws Exception {
        mockMvc.perform(get(base()).header(HttpHeaders.AUTHORIZATION, bearer(otherAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformOwner").value(false))
                .andExpect(jsonPath("$.onTrial").value(true));
        mockMvc.perform(get(base()).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformOwner").value(true));
    }

    @Test
    void otherAdminsGet403OnEveryOverride() throws Exception {
        mockMvc.perform(post(base() + "/extend-trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":7,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLATFORM_OWNER_ONLY"));
        mockMvc.perform(post(base() + "/end-trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(base() + "/override")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PREMIUM\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(base() + "/extend-trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(providerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":7,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerExtendsTrialThenSetsSubscription_providerSeesIt() throws Exception {
        mockMvc.perform(post(base() + "/extend-trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":10,\"reason\":\"Pilot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onTrial").value(true))
                .andExpect(jsonPath("$.history[0].action").value("EXTEND_TRIAL"))
                .andExpect(jsonPath("$.history[0].reason").value("Pilot"));

        mockMvc.perform(put(base() + "/override")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PREMIUM\",\"activeUntil\":\"2099-12-31\",\"reason\":\"Partner comp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.overridden").value(true))
                .andExpect(jsonPath("$.history.length()").value(2));

        mockMvc.perform(get("/api/provider/me/subscription/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(providerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ownerGets400ForBadInput() throws Exception {
        mockMvc.perform(post(base() + "/extend-trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":0,\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(base() + "/end-trial")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
