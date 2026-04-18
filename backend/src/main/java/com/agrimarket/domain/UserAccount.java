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
import jakarta.persistence.Convert;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Optional display name shown in the UI (e.g. "Philasande Bhani"). */
    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Hourly compensation for provider staff (not used for platform or provider owner). */
    @Convert(converter = StaffRateUnitConverter.class)
    @Column(name = "staff_compensation_method")
    private StaffRateUnit staffRateUnit;

    @Column(name = "staff_compensation_rate", precision = 14, scale = 2)
    private BigDecimal staffCompensationRate;

    public UserAccount(String email, String passwordHash, UserRole role, Provider provider) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.provider = provider;
    }
}
