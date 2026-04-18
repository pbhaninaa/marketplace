package com.agrimarket.security;

import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class MarketUserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final Long providerId;
    private final boolean enabled;

    public MarketUserPrincipal(UserAccount u) {
        this.userId = u.getId();
        this.email = u.getEmail();
        this.passwordHash = u.getPasswordHash();
        this.role = u.getRole();
        this.providerId = u.getProvider() != null ? u.getProvider().getId() : null;
        this.enabled = u.isEnabled();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
