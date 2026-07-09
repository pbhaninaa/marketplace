package com.agrimarket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Marks a completed order as paid out to a staff member (employer payroll). */
@Entity
@Table(
        name = "staff_payroll_job_marks",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_staff_payroll_order",
                        columnNames = {"staff_user_id", "order_id"}))
@Getter
@Setter
@NoArgsConstructor
public class StaffPayrollJobMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_user_id", nullable = false)
    private UserAccount staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by_user_id")
    private UserAccount markedBy;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt = Instant.now();

    /** When true, employer included bonus % on settlement (Wheel Hub includeBonus). */
    @Column(name = "include_bonus")
    private Boolean includeBonus;
}
