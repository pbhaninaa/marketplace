package com.agrimarket.api;

import com.agrimarket.api.dto.AdminDashboardStatsResponse;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.repo.UserAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final UserAccountRepository userAccountRepository;
    private final ProviderRepository providerRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final RentalBookingRepository rentalBookingRepository;

    @GetMapping("/stats")
    public AdminDashboardStatsResponse stats(
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        LocalDate safeTo = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate safeFrom = from == null ? safeTo.minusDays(30) : from;

        Instant fromTs = safeFrom.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toTs = safeTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long usersCreated = userAccountRepository.countCreatedBetween(fromTs, toTs);
        long providersCreated = providerRepository.countCreatedBetween(fromTs, toTs);
        long listingsCreated = listingRepository.countCreatedBetween(fromTs, toTs);
        long activeListings = listingRepository.countByActiveTrue();

        long ordersCount = orderRepository.countBetween(fromTs, toTs);
        BigDecimal ordersTotal = orderRepository.sumTotalBetween(fromTs, toTs);

        long rentalsCount = rentalBookingRepository.countBetween(fromTs, toTs);
        BigDecimal rentalsTotal = rentalBookingRepository.sumTotalBetween(fromTs, toTs);

        return new AdminDashboardStatsResponse(
                safeFrom,
                safeTo,
                usersCreated,
                providersCreated,
                listingsCreated,
                activeListings,
                ordersCount,
                ordersTotal == null ? BigDecimal.ZERO : ordersTotal,
                rentalsCount,
                rentalsTotal == null ? BigDecimal.ZERO : rentalsTotal);
    }
}

