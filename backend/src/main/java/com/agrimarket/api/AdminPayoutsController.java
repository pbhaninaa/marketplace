package com.agrimarket.api;

import com.agrimarket.api.dto.SettlementDtos.MarkPaidOutRequest;
import com.agrimarket.api.dto.SettlementDtos.MarkRefundedRequest;
import com.agrimarket.api.dto.SettlementDtos.PaymentSettlementRow;
import com.agrimarket.api.dto.SettlementDtos.PayoutActionResult;
import com.agrimarket.api.dto.SettlementDtos.PayoutSummaryResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.SettlementStatus;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.SellerSettlementService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seller payouts for online payments (platform admin only: {@code /api/admin/**} requires
 * PLATFORM_ADMIN in SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
public class AdminPayoutsController {

    /** Date filters are calendar days in South Africa. */
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final SellerSettlementService settlementService;

    @GetMapping("/summary")
    public PayoutSummaryResponse summary() {
        var sellers = settlementService.owedBySeller();
        BigDecimal totalOwed = sellers.stream().map(s -> s.amountOwed()).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PaymentSettlementRow> refunds = settlementService.list(SettlementStatus.REFUND_DUE, null, null, null);
        BigDecimal refundTotal = refunds.stream()
                .map(r -> r.amount() == null ? BigDecimal.ZERO : r.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayoutSummaryResponse(
                settlementService.currentFeePercent(), totalOwed, refunds.size(), refundTotal, sellers);
    }

    /**
     * Payments by settlement status (default HELD), optionally for one seller and a paid date range
     * ({@code from} / {@code to} inclusive, yyyy-MM-dd).
     */
    @GetMapping("/payments")
    public List<PaymentSettlementRow> payments(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "providerId", required = false) Long providerId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return settlementService.list(parseStatus(status), providerId, startOfDay(from), startOfDay(to, 1));
    }

    @PostMapping("/mark-paid")
    public PayoutActionResult markPaid(
            @AuthenticationPrincipal MarketUserPrincipal admin, @Valid @RequestBody MarkPaidOutRequest req) {
        return settlementService.markPaidOut(req.providerId(), req.paymentIds(), req.reference(), adminName(admin));
    }

    @PostMapping("/mark-refunded")
    public PayoutActionResult markRefunded(
            @AuthenticationPrincipal MarketUserPrincipal admin, @Valid @RequestBody MarkRefundedRequest req) {
        return settlementService.markRefunded(req.paymentIds(), req.reference(), adminName(admin));
    }

    private static String adminName(MarketUserPrincipal admin) {
        return admin == null ? null : admin.getUsername();
    }

    private static SettlementStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return SettlementStatus.HELD;
        }
        try {
            SettlementStatus s = SettlementStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (s == SettlementStatus.NONE) {
                throw new IllegalArgumentException();
            }
            return s;
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYOUT_STATUS", "Unknown payout status");
        }
    }

    private static Instant startOfDay(String raw) {
        return startOfDay(raw, 0);
    }

    private static Instant startOfDay(String raw, int plusDays) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).plusDays(plusDays).atStartOfDay(ZONE).toInstant();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE", "Dates must look like 2026-01-31");
        }
    }
}
