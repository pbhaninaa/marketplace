package com.agrimarket.api;

import com.agrimarket.api.dto.CategoryOptionResponse;
import com.agrimarket.api.dto.ListingFilterParams;
import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ProviderOptionResponse;
import com.agrimarket.domain.ListingType;
import com.agrimarket.repo.CategoryRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.service.ListingMapper;
import com.agrimarket.service.ListingSpecifications;
import com.agrimarket.service.SubscriptionService;
import com.agrimarket.service.RentalAvailability;
import com.agrimarket.repo.RentalBookingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMarketplaceController {

    private final ListingRepository listingRepository;
    private final CategoryRepository categoryRepository;
    private final ProviderRepository providerRepository;
    private final SubscriptionService subscriptionService;
    private final RentalBookingRepository rentalBookingRepository;
    private final Clock clock;

    @GetMapping("/categories")
    public java.util.List<CategoryOptionResponse> categories() {
        return categoryRepository.findAll(Sort.by("name").ascending()).stream()
                .map(c -> new CategoryOptionResponse(c.getId(), c.getName()))
                .toList();
    }

    @GetMapping("/category-options")
    public java.util.List<CategoryOptionResponse> categoryOptions(@RequestParam(required = false) ListingType listingType) {
        // simple: always return all categories
        return categoryRepository.findAll(Sort.by("name").ascending()).stream()
                .map(c -> new CategoryOptionResponse(c.getId(), c.getName()))
                .toList();
    }

    @GetMapping("/provider-options")
    public java.util.List<ProviderOptionResponse> providerOptions(@RequestParam(required = false) ListingType listingType) {
        // Only providers whose listings appear in the feed (i.e. that can take orders right now).
        return providerRepository.findAll(Sort.by("name").ascending()).stream()
                .filter(subscriptionService::canAcceptOrders)
                .map(p -> new ProviderOptionResponse(p.getId(), p.getName()))
                .toList();
    }

    /** A time range in which a rental item is already taken (no buyer details). */
    public record BookedSlot(Instant start, Instant end) {}

    /**
     * Upcoming taken slots for a rental item, so buyers can pick a free time before adding to cart.
     * Uses the same rule as booking: anything not cancelled holds its slot.
     */
    @GetMapping("/listings/{id}/booked")
    public java.util.List<BookedSlot> bookedSlots(@PathVariable("id") Long id) {
        Instant now = Instant.now(clock);
        return rentalBookingRepository
                .findOverlapping(id, RentalAvailability.BLOCKING, now, now.plus(java.time.Duration.ofDays(366)))
                .stream()
                .limit(100)
                .map(b -> new BookedSlot(b.getStartAt(), b.getEndAt()))
                .toList();
    }

    @GetMapping("/listings")
    public Page<ListingResponse> listings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) ListingType listingType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false, name = "location") String locationContains,
            @RequestParam(required = false, name = "search") String search) {

        var f = new ListingFilterParams(
                positiveId(categoryId),
                positiveId(providerId),
                listingType,
                meaningfulMinPrice(minPrice),
                maxPrice,
                blankToNull(locationContains),
                blankToNull(search));
        var pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return listingRepository
                .findAll(ListingSpecifications.publicFeed(f, Instant.now(clock)), pageable)
                .map(ListingMapper::toResponse);
    }

    private static Long positiveId(Long id) {
        return id != null && id > 0 ? id : null;
    }

    private static BigDecimal meaningfulMinPrice(BigDecimal min) {
        return min != null && min.signum() > 0 ? min : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

