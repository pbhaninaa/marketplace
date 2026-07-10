package com.agrimarket.security;

import com.agrimarket.config.AppProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/api/public/**", "/api/auth/login")
                        .permitAll()
                        .requestMatchers("/api/admin/**")
                        .hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/api/support/**")
                        .hasAnyRole("PLATFORM_ADMIN", "SUPPORT")
                        .requestMatchers("/api/provider/**")
                        .hasAnyRole(
                                "PROVIDER_OWNER",
                                "PROVIDER_ADMIN",
                                "PROVIDER_STAFF",
                                "PROVIDER_VIEWER")
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = resolveAllowedOrigins();
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
        List<String> configured = appProperties.cors() != null && appProperties.cors().allowedOrigins() != null
                ? appProperties.cors().allowedOrigins()
                : List.of();
        // Prefer explicit deploy env vars so indexed localhost defaults cannot win on UAT/PROD.
        String fromEnv = firstNonBlank(
                environment.getProperty("PROD_CORS_ORIGINS"),
                environment.getProperty("UAT_CORS_ORIGINS"),
                environment.getProperty("app.cors.allowed-origins"));
        if (fromEnv != null && !fromEnv.isBlank()) {
            configured = List.of(fromEnv);
        }
        if (configured.isEmpty()) {
            return List.of("http://localhost:5173");
        }
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String entry : configured) {
            if (entry == null || entry.isBlank()) continue;
            if (entry.contains(",")) {
                expanded.addAll(Arrays.stream(entry.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()));
            } else {
                expanded.add(entry.trim());
            }
        }
        return expanded.isEmpty() ? List.of("http://localhost:5173") : new ArrayList<>(expanded);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
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
