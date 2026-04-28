package com.agrimarket.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);
        try {
            Claims claims = jwtService.parse(token);
            String subject = claims.getSubject();
            if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String email = claims.get("email", String.class);
                String roleStr = claims.get("role", String.class);
                Long pid = claims.get("pid", Long.class);
                Boolean imp = claims.get("imp", Boolean.class);

                UserDetails loaded = userDetailsService.loadUserByUsername(email);
                if (!(loaded instanceof MarketUserPrincipal dbPrincipal)) {
                    throw new IllegalStateException("Invalid principal type");
                }
                if (!dbPrincipal.isEnabled()) {
                    throw new IllegalStateException("User disabled");
                }

                com.agrimarket.domain.UserRole claimedRole = com.agrimarket.domain.UserRole.valueOf(roleStr);

                boolean isImpersonation = Boolean.TRUE.equals(imp);
                MarketUserPrincipal effectivePrincipal;

                if (!isImpersonation) {
                    // Normal tokens must match current DB role/provider to avoid privilege escalation.
                    if (dbPrincipal.getRole() != claimedRole) {
                        throw new IllegalStateException("Role mismatch");
                    }
                    if (dbPrincipal.getProviderId() == null) {
                        if (pid != null) throw new IllegalStateException("Provider mismatch");
                    } else {
                        if (pid == null || !dbPrincipal.getProviderId().equals(pid)) {
                            throw new IllegalStateException("Provider mismatch");
                        }
                    }
                    effectivePrincipal = dbPrincipal;
                } else {
                    // Support/Admin shadowing: allow overriding role/providerId from token claims.
                    if (!(dbPrincipal.getRole() == com.agrimarket.domain.UserRole.SUPPORT
                            || dbPrincipal.getRole() == com.agrimarket.domain.UserRole.PLATFORM_ADMIN)) {
                        throw new IllegalStateException("Impersonation not allowed");
                    }
                    if (pid == null) {
                        throw new IllegalStateException("Missing provider for impersonation");
                    }
                    effectivePrincipal = new MarketUserPrincipal(
                            dbPrincipal.getUserId(),
                            dbPrincipal.getEmail(),
                            dbPrincipal.getPasswordHash(),
                            claimedRole,
                            pid,
                            true
                    );
                }

                var auth = new UsernamePasswordAuthenticationToken(
                        effectivePrincipal, null, effectivePrincipal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }
}
