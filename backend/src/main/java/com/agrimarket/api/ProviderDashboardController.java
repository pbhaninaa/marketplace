package com.agrimarket.api;

import com.agrimarket.api.dto.ProviderDashboardStatsResponse;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me/dashboard")
@RequiredArgsConstructor
public class ProviderDashboardController {

    private final OrderRepository orderRepository;
    private final RentalBookingRepository rentalBookingRepository;

    @GetMapping("/stats")
    public ProviderDashboardStatsResponse stats(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        LocalDate safeTo = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate safeFrom = from == null ? safeTo.minusDays(30) : from;

        Instant fromTs = safeFrom.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toTs = safeTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long ordersCount = orderRepository.countForProviderBetween(actor.getProviderId(), fromTs, toTs);
        BigDecimal ordersTotal = orderRepository.sumTotalForProviderBetween(actor.getProviderId(), fromTs, toTs);

        long rentalsCount = rentalBookingRepository.countForProviderBetween(actor.getProviderId(), fromTs, toTs);
        BigDecimal rentalsTotal = rentalBookingRepository.sumTotalForProviderBetween(actor.getProviderId(), fromTs, toTs);

        return new ProviderDashboardStatsResponse(
                safeFrom,
                safeTo,
                ordersCount,
                ordersTotal == null ? BigDecimal.ZERO : ordersTotal,
                rentalsCount,
                rentalsTotal == null ? BigDecimal.ZERO : rentalsTotal);
    }
}

