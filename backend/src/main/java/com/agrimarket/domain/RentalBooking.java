package com.agrimarket.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rental_bookings")
@Getter
@Setter
@NoArgsConstructor
public class RentalBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔴 Prevent lazy crash
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnore
    private Provider provider;


    // 🔴 Also lazy + chained to Provider
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    @JsonIgnore
    private Listing listing;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Column(name = "guest_email", nullable = false)
    private String guestEmail;

    @Column(name = "guest_phone", nullable = false)
    private String guestPhone;

    @Column(name = "delivery_or_pickup", length = 4000, nullable = false)
    private String deliveryOrPickup;

    @Column(name="delivery_address", nullable = true)
    private String deliveryAddress;
    @Column(name="latitude", nullable = true)
    private String latitude;
    @Column(name="longitude", nullable = true)
    private String longitude;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "delivery_distance_km", precision = 10, scale = 2)
    private BigDecimal deliveryDistanceKm;

    @Column(name = "delivery_fee", precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "session_key")
    private String sessionKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "verification_code", nullable = false, unique = true, length = 9)
    private String verificationCode;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}