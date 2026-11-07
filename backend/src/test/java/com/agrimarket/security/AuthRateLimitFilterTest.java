package com.agrimarket.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.config.AuthSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-07T10:00:00Z"));
    private final AuthRateLimitFilter filter =
            new AuthRateLimitFilter(new RateLimiter(clock, true), new ObjectMapper(), new AuthSecurityProperties());

    private MockHttpServletResponse post(String path, String xff) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        // MockMvc leaves the servlet path empty; the filter must still match on the request URI.
        req.setServletPath("");
        req.setRemoteAddr("10.0.0.1");
        if (xff != null) req.addHeader("X-Forwarded-For", xff);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void blocksTheEleventhLoginInAMinuteFromOneIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(post("/api/auth/login", "203.0.113.5").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = post("/api/auth/login", "203.0.113.5");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getContentAsString()).contains("Too many attempts").contains("RATE_LIMITED");

        // Another client is unaffected.
        assertThat(post("/api/auth/login", "198.51.100.7").getStatus()).isEqualTo(200);
    }

    @Test
    void spoofedLeftmostForwardedForDoesNotDodgeTheLimit() throws Exception {
        for (int i = 0; i < 10; i++) {
            post("/api/auth/login", "1.1.1." + i + ", 203.0.113.5");
        }
        assertThat(post("/api/auth/login", "9.9.9.9, 203.0.113.5").getStatus()).isEqualTo(429);
    }

    @Test
    void otherPathsAndMethodsPassThrough() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(post("/api/public/cart/session", null).getStatus()).isEqualTo(200);
        }
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/auth/login");
        assertThat(filter.shouldNotFilter(get)).isTrue();
    }

    @Test
    void forgotPasswordRegisterAndOtpHaveTightLimits() throws Exception {
        for (int i = 0; i < 5; i++) post("/api/public/forgot-password", null);
        assertThat(post("/api/public/forgot-password", null).getStatus()).isEqualTo(429);

        for (int i = 0; i < 10; i++) post("/api/public/provider/register", null);
        assertThat(post("/api/public/provider/register", null).getStatus()).isEqualTo(429);

        // Guest client codes and sign-up codes share one allowance per IP.
        for (int i = 0; i < 3; i++) post("/api/public/client/otp/request", null);
        for (int i = 0; i < 2; i++) post("/api/public/provider/register/email-code", null);
        assertThat(post("/api/public/client/otp/request", null).getStatus()).isEqualTo(429);
        assertThat(post("/api/public/provider/register/email-code/", null).getStatus()).isEqualTo(429);
    }

    @Test
    void limitsComeFromConfiguration() throws Exception {
        AuthSecurityProperties props = new AuthSecurityProperties();
        props.getRateLimit().setLoginPerMinute(2);
        AuthRateLimitFilter strict = new AuthRateLimitFilter(new RateLimiter(clock, true), new ObjectMapper(), props);
        MockHttpServletResponse last = null;
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.2");
            last = new MockHttpServletResponse();
            strict.doFilter(req, last, new MockFilterChain());
        }
        assertThat(last.getStatus()).isEqualTo(429);
    }

    @Test
    void tooManyRequestsMessageRoundsUpToMinutes() {
        assertThat(AuthRateLimitFilter.tooManyRequestsMessage(30)).contains("1 minute ");
        assertThat(AuthRateLimitFilter.tooManyRequestsMessage(61)).contains("2 minutes");
    }
}
