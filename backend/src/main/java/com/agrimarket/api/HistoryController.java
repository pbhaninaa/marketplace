package com.agrimarket.api;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.UserRole;
import com.agrimarket.security.JwtService;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.history.HistoryFilter;
import com.agrimarket.service.history.HistoryReports;
import com.agrimarket.service.history.HistoryScope;
import com.agrimarket.service.history.HistoryService;
import com.agrimarket.service.history.HistoryService.HistoryPage;
import com.agrimarket.service.history.HistoryService.OrderRow;
import com.agrimarket.service.history.HistoryService.PaymentRow;
import com.agrimarket.service.history.SpreadsheetExport;
import io.jsonwebtoken.Claims;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified, filterable order &amp; payment history:
 * <ul>
 *   <li>{@code /api/admin/history/**} — every provider (admin only, enforced by SecurityConfig)</li>
 *   <li>{@code /api/provider/me/history/**} — the signed-in provider's own records only</li>
 *   <li>{@code /api/public/client/history/**} — a client's own orders, identified by the email-code token</li>
 * </ul>
 * Query params: status, paymentStatus, method, providerId (admin), from, to (yyyy-MM-dd, inclusive),
 * kind (payments: ORDER|RENTAL), q, page, size (max 200). Exports take the same filters plus
 * format=csv|xlsx and include every matching row.
 */
@RestController
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;
    private final JwtService jwtService;

    /** Bound from query parameters. */
    public record FilterParams(
            String status,
            String paymentStatus,
            String method,
            Long providerId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            String kind,
            String q) {

        HistoryFilter toFilter() {
            return HistoryFilter.of(status, paymentStatus, method, providerId, from, to, kind, q);
        }
    }

    // ---------------------------------------------------------------- admin

    @GetMapping("/api/admin/history/orders")
    public HistoryPage<OrderRow> adminOrders(
            @ModelAttribute FilterParams f,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return historyService.orders(HistoryScope.admin(), f.toFilter(), page, size);
    }

    @GetMapping("/api/admin/history/payments")
    public HistoryPage<PaymentRow> adminPayments(
            @ModelAttribute FilterParams f,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return historyService.payments(HistoryScope.admin(), f.toFilter(), page, size);
    }

    @GetMapping("/api/admin/history/orders/export")
    public ResponseEntity<byte[]> adminOrdersExport(
            @ModelAttribute FilterParams f, @RequestParam(defaultValue = "csv") String format) {
        return HistoryReports.orders(
                historyService.allOrders(HistoryScope.admin(), f.toFilter()), SpreadsheetExport.Format.parse(format), true);
    }

    @GetMapping("/api/admin/history/payments/export")
    public ResponseEntity<byte[]> adminPaymentsExport(
            @ModelAttribute FilterParams f, @RequestParam(defaultValue = "csv") String format) {
        return HistoryReports.payments(
                historyService.allPayments(HistoryScope.admin(), f.toFilter()), SpreadsheetExport.Format.parse(format), true);
    }

    // ---------------------------------------------------------------- provider

    @GetMapping("/api/provider/me/history/orders")
    public HistoryPage<OrderRow> providerOrders(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @ModelAttribute FilterParams f,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return historyService.orders(HistoryScope.provider(actor), f.toFilter(), page, size);
    }

    @GetMapping("/api/provider/me/history/payments")
    public HistoryPage<PaymentRow> providerPayments(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @ModelAttribute FilterParams f,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return historyService.payments(HistoryScope.provider(actor), f.toFilter(), page, size);
    }

    @GetMapping("/api/provider/me/history/orders/export")
    public ResponseEntity<byte[]> providerOrdersExport(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @ModelAttribute FilterParams f,
            @RequestParam(defaultValue = "csv") String format) {
        return HistoryReports.orders(
                historyService.allOrders(HistoryScope.provider(actor), f.toFilter()),
                SpreadsheetExport.Format.parse(format),
                false);
    }

    @GetMapping("/api/provider/me/history/payments/export")
    public ResponseEntity<byte[]> providerPaymentsExport(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @ModelAttribute FilterParams f,
            @RequestParam(defaultValue = "csv") String format) {
        return HistoryReports.payments(
                historyService.allPayments(HistoryScope.provider(actor), f.toFilter()),
                SpreadsheetExport.Format.parse(format),
                false);
    }

    // ---------------------------------------------------------------- client

    @GetMapping("/api/public/client/history/orders")
    public HistoryPage<OrderRow> clientOrders(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @ModelAttribute FilterParams f,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return historyService.orders(clientScope(authorization), f.toFilter(), page, size);
    }

    @GetMapping("/api/public/client/history/payments")
    public HistoryPage<PaymentRow> clientPayments(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @ModelAttribute FilterParams f,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return historyService.payments(clientScope(authorization), f.toFilter(), page, size);
    }

    /**
     * Client sessions come from the email one-time-code flow and usually have no user account, so the
     * signed token is verified here directly: it must be a CLIENT token, and its email (the verified
     * OTP target) is the only customer whose records are returned.
     */
    private HistoryScope clientScope(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Sign in with your email code first");
        }
        Claims claims;
        try {
            claims = jwtService.parse(authorization.substring(7));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Your session has expired. Sign in again.");
        }
        if (!UserRole.CLIENT.name().equals(claims.get("role", String.class))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH", "Order history is for customer accounts");
        }
        return HistoryScope.client(claims.get("email", String.class));
    }
}
