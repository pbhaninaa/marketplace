package com.agrimarket.security;

import com.agrimarket.config.AuthSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP rate limits on the public auth endpoints, so a single source cannot hammer login,
 * sign-up, password reset or the email OTP flow. Over the limit: 429 with Retry-After and the same
 * JSON error shape as {@code GlobalExceptionHandler} ({@code code}, {@code errorCode}, {@code message}).
 *
 * <p>Per-account protection (lockout) lives in {@link LoginAttemptService}; per-email caps on
 * reset / OTP emails live in the services.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    record Rule(String name, int limit, Duration window) {}

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final int trustedProxyHops;
    /** POST path → limits per client IP. Several rules = all must pass (burst + sustained). */
    private final Map<String, List<Rule>> rules;

    public AuthRateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper, AuthSecurityProperties props) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.trustedProxyHops = props.getTrustedProxyHops();
        this.rules = buildRules(props.getRateLimit());
    }

    static Map<String, List<Rule>> buildRules(AuthSecurityProperties.RateLimit r) {
        Duration min = Duration.ofMinutes(1);
        Duration quarter = Duration.ofMinutes(15);
        Duration hour = Duration.ofHours(1);
        List<Rule> otpRequest = List.of(new Rule("otp-request-15m", r.getOtpRequestPer15Min(), quarter));
        return Map.of(
                "/api/auth/login", List.of(
                        new Rule("login-1m", r.getLoginPerMinute(), min),
                        new Rule("login-1h", r.getLoginPerHour(), hour)),
                "/api/auth/change-password", List.of(
                        new Rule("change-password-15m", r.getChangePasswordPer15Min(), quarter)),
                "/api/public/provider/register", List.of(
                        new Rule("register-1h", r.getRegisterPerHour(), hour)),
                "/api/public/first-admin", List.of(
                        new Rule("first-admin-1h", r.getRegisterPerHour(), hour)),
                "/api/public/forgot-password", List.of(
                        new Rule("forgot-15m", r.getForgotPasswordPer15Min(), quarter)),
                "/api/public/reset-password", List.of(
                        new Rule("reset-15m", r.getResetPasswordPer15Min(), quarter)),
                // Sign-up email codes and guest client codes share one allowance per IP.
                "/api/public/client/otp/request", otpRequest,
                "/api/public/provider/register/email-code", otpRequest,
                "/api/public/client/otp/verify", List.of(
                        new Rule("otp-verify-15m", r.getOtpVerifyPer15Min(), quarter)));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!rateLimiter.isEnabled() || !"POST".equalsIgnoreCase(request.getMethod())) return true;
        return !rules.containsKey(pathOf(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = pathOf(request);
        String ip = ClientIpResolver.resolve(request, trustedProxyHops);
        for (Rule rule : rules.get(path)) {
            RateLimiter.Decision d = rateLimiter.tryAcquire(rule.name() + ":ip:" + ip, rule.limit(), rule.window());
            if (!d.allowed()) {
                log.warn("Rate limit {} exceeded for {} on {}", rule.name(), ip, path);
                writeTooManyRequests(response, objectMapper, d.retryAfterSeconds());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Request path without the context path. Uses the request URI rather than
     * {@code getServletPath()}, which is empty in some setups (e.g. MockMvc) and would silently
     * skip the rules.
     */
    static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            String sp = request.getServletPath();
            return sp != null ? sp : "";
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri.isEmpty() ? "/" : uri;
    }

    static void writeTooManyRequests(HttpServletResponse response, ObjectMapper mapper, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of(
                "code", "RATE_LIMITED",
                "errorCode", "RATE_LIMITED",
                "message", tooManyRequestsMessage(retryAfterSeconds)));
    }

    static String tooManyRequestsMessage(long retryAfterSeconds) {
        long minutes = Math.max(1, (retryAfterSeconds + 59) / 60);
        return "Too many attempts. Please wait " + minutes + (minutes == 1 ? " minute" : " minutes")
                + " and try again.";
    }
}
