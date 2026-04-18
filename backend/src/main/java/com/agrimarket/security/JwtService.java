package com.agrimarket.security;

import com.agrimarket.config.AppProperties;
import com.agrimarket.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;

    public String createToken(long userId, String email, UserRole role, Long providerId) {
        long now = System.currentTimeMillis();
        long exp = now + appProperties.jwt().expirationMs();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(new Date(now))
                .expiration(new Date(exp));
        if (providerId != null) {
            builder.claim("pid", providerId);
        }
        return builder.signWith(signingKey()).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
