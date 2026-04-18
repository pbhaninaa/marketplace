package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure unit tests — no Spring context. */
class RentalPricingServiceTest {

    private RentalPricingService pricing;
    private Listing rental;

    @BeforeEach
    void setUp() {
        pricing = new RentalPricingService();
        Provider p = new Provider("P", "p", "d", "L");
        p.setStatus(ProviderStatus.ACTIVE);
        Category c = new Category("C", "c");
        rental = new Listing();
        rental.setProvider(p);
        rental.setCategory(c);
        rental.setListingType(ListingType.RENT);
        rental.setTitle("Trailer");
        rental.setUnitPrice(new BigDecimal("100.00"));
        rental.setRentPriceDaily(new BigDecimal("450.00"));
        rental.setRentPriceHourly(new BigDecimal("80.00"));
        rental.setRentPriceWeekly(new BigDecimal("2400.00"));
        rental.setActive(true);
    }

    @Test
    void usesDailyRateWhenWithinWeekWindow() {
        Instant start = Instant.parse("2026-06-01T08:00:00Z");
        Instant end = start.plus(2, ChronoUnit.DAYS);
        BigDecimal amount = pricing.priceRental(rental, start, end);
        assertThat(amount).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    /** Inclusive Jun 1–3: start midnight Jun 1, exclusive end midnight Jun 4 → 3 billable days in Johannesburg. */
    @Test
    void threeInclusiveCalendarDays() {
        Instant start = Instant.parse("2026-06-01T00:00:00+02:00");
        Instant end = Instant.parse("2026-06-04T00:00:00+02:00");
        BigDecimal amount = pricing.priceRental(rental, start, end);
        assertThat(amount).isEqualByComparingTo(new BigDecimal("1350.00"));
    }

    @Test
    void singleCalendarDayRental() {
        Instant start = Instant.parse("2026-06-10T00:00:00+02:00");
        Instant end = Instant.parse("2026-06-11T00:00:00+02:00");
        BigDecimal amount = pricing.priceRental(rental, start, end);
        assertThat(amount).isEqualByComparingTo(new BigDecimal("450.00"));
    }

    @Test
    void rejectsNonRentalListing() {
        rental.setListingType(ListingType.SALE);
        Instant start = Instant.now();
        Instant end = start.plus(5, ChronoUnit.HOURS);
        assertThatThrownBy(() -> pricing.priceRental(rental, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a rental");
    }

    @Test
    void rejectsInvalidWindow() {
        Instant start = Instant.now();
        assertThatThrownBy(() -> pricing.priceRental(rental, start, start))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
