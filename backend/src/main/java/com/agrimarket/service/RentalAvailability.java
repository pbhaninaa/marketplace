package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.RentalBookingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * One rental item can only be booked once for any moment in time.
 *
 * <p>A slot is taken by every booking that is not cancelled: awaiting payment (cash/EFT bookings are real
 * until the seller confirms or cancels them), paid, or collected (the item is out on hire). Overlap means
 * the windows share any time: back-to-back bookings (one ends exactly when the next starts) are allowed.
 *
 * <p>Checkout locks the listing row before calling {@link #requireFree}, so two buyers checking out the
 * same item at the same moment are handled one after the other and the second one is refused.
 */
@Service
@RequiredArgsConstructor
public class RentalAvailability {

    /** Bookings that hold their time slot. Only CANCELLED frees it. */
    public static final Set<BookingStatus> BLOCKING =
            EnumSet.of(BookingStatus.PENDING_PAYMENT, BookingStatus.PAID, BookingStatus.COLLECTED);

    /** Rentals are booked by calendar day (start = first day 00:00), judged in South African time. */
    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZONE);

    private final RentalBookingRepository rentalBookingRepository;
    private final Clock clock;

    /** End after start, and the first rental day not before today (so a same-day rental is allowed). */
    public void validateWindow(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RENTAL_DATES_REQUIRED", "Choose a start and end for the rental.");
        }
        if (!end.isAfter(start)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "RENTAL_DATES_INVALID", "The rental must end after it starts.");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        if (LocalDate.ofInstant(start, ZONE).isBefore(today)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "RENTAL_DATES_INVALID", "The rental can't start in the past.");
        }
    }

    /** Throws 409 RENTAL_CONFLICT if any non-cancelled booking of this item overlaps the window. */
    public void requireFree(Listing listing, Instant start, Instant end) {
        List<RentalBooking> conflicts =
                rentalBookingRepository.findOverlapping(listing.getId(), BLOCKING, start, end);
        if (!conflicts.isEmpty()) {
            RentalBooking first = conflicts.get(0);
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RENTAL_CONFLICT",
                    String.format(
                            "'%s' is already booked from %s to %s. Please choose a different time.",
                            listing.getTitle(), WHEN.format(first.getStartAt()), WHEN.format(first.getEndAt())));
        }
    }

    /**
     * Throws 409 RENTAL_IN_CART if one of these cart lines already holds the same item for an overlapping
     * time (a cart, or one checkout, can't book the same item twice for the same slot).
     */
    public void requireNotInLines(Listing listing, Instant start, Instant end, Collection<CartLine> lines, CartLine except) {
        for (CartLine line : lines) {
            if (line == except
                    || line.getListing() == null
                    || !line.getListing().getId().equals(listing.getId())
                    || line.getRentalStart() == null
                    || line.getRentalEnd() == null) {
                continue;
            }
            if (overlaps(start, end, line.getRentalStart(), line.getRentalEnd())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "RENTAL_IN_CART",
                        String.format(
                                "'%s' is already in your cart from %s to %s. One item can only be booked once for the same time.",
                                listing.getTitle(), WHEN.format(line.getRentalStart()), WHEN.format(line.getRentalEnd())));
            }
        }
    }

    static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }
}
