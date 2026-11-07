package com.agrimarket.security;

import com.agrimarket.config.AppProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppProperties appProperties;
    private final Environment environment;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(SecurityPaths.PUBLIC)
                        .permitAll()
                        .requestMatchers(SecurityPaths.ADMIN)
                        .hasRole("PLATFORM_ADMIN")
                        .requestMatchers(SecurityPaths.SUPPORT)
                        .hasAnyRole(SecurityPaths.SUPPORT_ROLES)
                        .requestMatchers(SecurityPaths.PROVIDER)
                        .hasAnyRole(SecurityPaths.PROVIDER_ROLES)
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = resolveAllowedOrigins();
        log.info("CORS allowed origins: {}", origins);
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization", "X-Session-Id"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private List<String> resolveAllowedOrigins() {
        LinkedHashSet<String> origins = new LinkedHashSet<>();

        // Deploy env vars first (highest priority for UAT/PROD).
        addOrigins(origins, environment.getProperty("PROD_CORS_ORIGINS"));
        addOrigins(origins, environment.getProperty("UAT_CORS_ORIGINS"));

        // Always allow the public frontend URL used for emails / password reset.
        addOrigins(origins, environment.getProperty("PUBLIC_APP_BASE_URL"));
        addOrigins(origins, environment.getProperty("APP_FRONTEND_URL"));
        addOrigins(origins, environment.getProperty("app.password-reset.public-app-base-url"));
        addOrigins(origins, environment.getProperty("app.email.public-app-base-url"));

        // Profile property (comma-separated string).
        if (appProperties.cors() != null) {
            addOrigins(origins, appProperties.cors().allowedOrigins());
        }
        addOrigins(origins, environment.getProperty("app.cors.allowed-origins"));

        if (isDeployedProfile()) {
            // Fail closed on UAT/PROD: never fall back to (or keep) a dev origin. An empty list rejects every
            // cross-origin request; DeployedProfileValidator also refuses to start without UAT/PROD_CORS_ORIGINS.
            boolean removed = origins.removeIf(SecurityConfig::isLocalOrigin);
            if (removed) {
                log.warn("Ignoring localhost CORS origins on a deployed profile");
            }
            if (origins.isEmpty()) {
                log.error("No CORS origins configured for a deployed profile; cross-origin requests will be rejected");
            }
            return new ArrayList<>(origins);
        }
        if (origins.isEmpty()) {
            origins.add("http://localhost:5173");
        }
        return new ArrayList<>(origins);
    }

    private boolean isDeployedProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("uat".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    static boolean isLocalOrigin(String origin) {
        String o = origin.toLowerCase(java.util.Locale.ROOT);
        return o.contains("://localhost") || o.contains("://127.0.0.1") || o.contains("://[::1]");
    }

    private static void addOrigins(LinkedHashSet<String> target, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                .forEach(target::add);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
