package com.agrimarket.api.dto;

import com.agrimarket.domain.RentalBooking;
import java.time.Instant;

/**
 * Response DTO for rental booking verification operations.
 * Prevents Hibernate lazy loading issues by not exposing entity relationships.
 */
public record BookingVerificationResponse(
        String message,
        Instant verifiedAt,
        Long bookingId,
        String guestName,
        String guestPhone,
        String guestEmail,
        String status) {
    public static BookingVerificationResponse from(RentalBooking booking, String message) {
        return new BookingVerificationResponse(
                message,
                booking.getVerifiedAt(),
                booking.getId(),
                booking.getGuestName(),
                booking.getGuestPhone(),
                booking.getGuestEmail(),
                booking.getStatus().toString());
    }
}