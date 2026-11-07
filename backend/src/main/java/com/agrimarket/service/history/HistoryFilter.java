package com.agrimarket.service.history;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PaymentRecordStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Filters shared by the order and payment history pages. All fields are optional.
 *
 * @param status order status (orders) — ignored for payments
 * @param paymentStatus {@code PENDING}/{@code PAID}; for payments matches the payment record status
 * @param method payment method (CASH, EFT, PEACH)
 * @param providerId restrict to one provider (admin only; providers are always forced to their own)
 * @param from inclusive start date (UTC day, same convention as the dashboards)
 * @param to inclusive end date
 * @param kind payments only: {@code ORDER} or {@code RENTAL}
 * @param q free text: customer name / email / phone or numeric id
 */
public record HistoryFilter(
        OrderStatus status,
        String paymentStatus,
        PaymentMethod method,
        Long providerId,
        LocalDate from,
        LocalDate to,
        String kind,
        String q) {

    public static HistoryFilter of(
            String status,
            String paymentStatus,
            String method,
            Long providerId,
            LocalDate from,
            LocalDate to,
            String kind,
            String q) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE", "'from' must be on or before 'to'");
        }
        return new HistoryFilter(
                parseEnum(OrderStatus.class, status, "status"),
                normalizePaymentStatus(paymentStatus),
                blank(method) ? null : parseMethod(method),
                providerId,
                from,
                to,
                normalizeKind(kind),
                blank(q) ? null : q.trim());
    }

    public HistoryFilter withProvider(Long forcedProviderId) {
        return new HistoryFilter(status, paymentStatus, method, forcedProviderId, from, to, kind, q);
    }

    Instant fromInstant() {
        return from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** Exclusive upper bound. */
    Instant toInstantExclusive() {
        return to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    PaymentRecordStatus paymentRecordStatus() {
        if (paymentStatus == null) return null;
        return "PAID".equals(paymentStatus) ? PaymentRecordStatus.PAID : PaymentRecordStatus.PENDING_PAYMENT;
    }

    com.agrimarket.domain.PaymentStatus orderPaymentStatus() {
        if (paymentStatus == null) return null;
        return "PAID".equals(paymentStatus)
                ? com.agrimarket.domain.PaymentStatus.PAID
                : com.agrimarket.domain.PaymentStatus.PENDING;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank() || "ALL".equalsIgnoreCase(s.trim());
    }

    private static String normalizePaymentStatus(String raw) {
        if (blank(raw)) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "PAID" -> "PAID";
            case "PENDING", "PENDING_PAYMENT" -> "PENDING";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "FILTER", "Unknown payment status: " + raw);
        };
    }

    private static String normalizeKind(String raw) {
        if (blank(raw)) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (!v.equals("ORDER") && !v.equals("RENTAL")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTER", "Unknown payment kind: " + raw);
        }
        return v;
    }

    private static PaymentMethod parseMethod(String raw) {
        try {
            return PaymentMethod.fromJson(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTER", "Unknown payment method: " + raw);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String name) {
        if (blank(raw)) return null;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILTER", "Unknown " + name + ": " + raw);
        }
    }
}
