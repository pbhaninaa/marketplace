package com.agrimarket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "provider_staff_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider_id", "user_id", "permission_key"}))
@Getter
@Setter
@NoArgsConstructor
public class ProviderStaffPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_key", nullable = false, length = 64)
    private ProviderPermissionKey permissionKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ProviderStaffPermission(Provider provider, UserAccount user, ProviderPermissionKey permissionKey) {
        this.provider = provider;
        this.user = user;
        this.permissionKey = permissionKey;
    }
}

