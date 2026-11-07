package com.agrimarket.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigCorsTest {

    @Test
    void localProfile_fallsBackToViteDevOrigin() {
        assertThat(allowedOrigins(new MockEnvironment(), null)).containsExactly("http://localhost:5173");
    }

    @Test
    void deployedProfile_withoutOrigins_failsClosed() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThat(allowedOrigins(env, null)).isEmpty();
    }

    @Test
    void deployedProfile_dropsLocalhostButKeepsConfiguredFrontend() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("UAT_CORS_ORIGINS", "https://uat.example.com/, http://localhost:5173");
        env.setActiveProfiles("uat");
        assertThat(allowedOrigins(env, "http://127.0.0.1:5173")).containsExactly("https://uat.example.com");
    }

    private static List<String> allowedOrigins(MockEnvironment env, String profileOrigins) {
        AppProperties props = new AppProperties(
                "TEST", new AppProperties.Cors(profileOrigins), null, null, null, null, false);
        SecurityConfig config = new SecurityConfig(null, null, props, env);
        CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(new MockHttpServletRequest("GET", "/api/public/listings"));
        assertThat(cors).isNotNull();
        List<String> origins = cors.getAllowedOrigins();
        return origins == null ? List.of() : origins;
    }
}
