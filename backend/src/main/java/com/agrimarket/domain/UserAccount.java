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
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** BCrypt hash. Never serialised: API responses use DTOs, and this guards against returning the entity. */
    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // ---- Login lockout (see security.LoginAttemptService) ----

    /** Consecutive failed logins since the last success or lock. Null = 0 (existing rows). */
    @JsonIgnore
    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts;

    /** Login is refused until this instant after too many failures. Null = not locked. */
    @JsonIgnore
    @Column(name = "locked_until")
    private Instant lockedUntil;

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

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 40)
    private String phoneNumber;

    /** Hourly compensation for provider staff (not used for platform or provider owner). */
    @Convert(converter = StaffRateUnitConverter.class)
    @Column(name = "staff_compensation_method")
    private StaffRateUnit staffRateUnit;

    @Column(name = "staff_compensation_rate", precision = 14, scale = 2)
    private BigDecimal staffCompensationRate;

    /** Provider-defined target period: DAILY / WEEKLY / MONTHLY. */
    @Column(name = "staff_target_period", length = 20)
    private String staffTargetPeriod;

    @Column(name = "staff_target_value", precision = 14, scale = 2)
    private BigDecimal staffTargetValue;

    /** Bonus percentage 0–100. */
    @Column(name = "staff_bonus_percentage", precision = 6, scale = 2)
    private BigDecimal staffBonusPercentage;

    public UserAccount(String email, String passwordHash, UserRole role, Provider provider) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.provider = provider;
    }
}
