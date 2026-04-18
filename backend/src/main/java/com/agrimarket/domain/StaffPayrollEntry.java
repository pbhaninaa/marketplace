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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "staff_payroll_entries")
@Getter
@Setter
@NoArgsConstructor
public class StaffPayrollEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_user_id", nullable = false)
    private UserAccount staff;

    @Column(name = "units_worked", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitsWorked;

    @Column(name = "rate_snapshot", nullable = false, precision = 14, scale = 2)
    private BigDecimal rateSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_unit_snapshot", nullable = false)
    private StaffRateUnit rateUnitSnapshot;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 2000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private UserAccount recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
