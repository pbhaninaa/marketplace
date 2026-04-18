package com.agrimarket.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JwtServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void createAndParseToken_roundTrip() {
        String token = jwtService.createToken(42L, "user@example.com", UserRole.PROVIDER_OWNER, 7L);
        assertThat(token).isNotBlank();

        var claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("PROVIDER_OWNER");
        Object pid = claims.get("pid");
        assertThat(pid).isNotNull();
        assertThat(Long.valueOf(pid.toString())).isEqualTo(7L);
    }
}
