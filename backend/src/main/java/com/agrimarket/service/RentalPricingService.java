package com.agrimarket.service;

import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class RentalPricingService {

    /** Billing calendar for rental days (inclusive count uses exclusive end instant in this zone). */
    private static final ZoneId MARKET_ZONE = ZoneId.of("Africa/Johannesburg");

    /**
     * Price from calendar days between {@code start} and {@code end}, where {@code end} is exclusive
     * (first instant after the rental period), aligned to {@link #MARKET_ZONE} dates.
     */
    public BigDecimal priceRental(Listing listing, Instant start, Instant end) {
        if (listing.getListingType() != ListingType.RENT) {
            throw new IllegalArgumentException("Not a rental listing");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Invalid rental window");
        }
        long days = ChronoUnit.DAYS.between(start.atZone(MARKET_ZONE).toLocalDate(), end.atZone(MARKET_ZONE).toLocalDate());
        if (days < 1) {
            throw new IllegalArgumentException("Invalid rental window");
        }

        if (listing.getRentPriceWeekly() != null && days >= 6) {
            long weeks = Math.max(1, (long) Math.ceil(days / 7.0));
            return listing.getRentPriceWeekly().multiply(BigDecimal.valueOf(weeks));
        }
        if (listing.getRentPriceDaily() != null) {
            return listing.getRentPriceDaily().multiply(BigDecimal.valueOf(days));
        }
        if (listing.getRentPriceHourly() != null) {
            long hours = days * 24L;
            return listing.getRentPriceHourly().multiply(BigDecimal.valueOf(hours));
        }
        return listing.getUnitPrice().multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
    }
}
