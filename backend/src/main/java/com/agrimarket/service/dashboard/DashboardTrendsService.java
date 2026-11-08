package com.agrimarket.service.dashboard;

import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.Subscription;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.service.ProviderTrialSupport;
import com.agrimarket.service.SubscriptionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Time series and breakdowns behind the dashboard charts. Platform scope (admin) when
 * {@code providerId} is null, otherwise limited to that provider's own orders, rentals and payments.
 *
 * <p>Revenue excludes cancelled orders and bookings. Dates are UTC days, like the KPI tiles.
 */
@Service
@RequiredArgsConstructor
public class DashboardTrendsService {

    /** Ranges longer than this are grouped by week so columns stay readable. */
    static final int MAX_DAILY_BUCKETS = 62;

    static final int TOP_N = 5;

    private final EntityManager em;
    private final ProviderRepository providerRepository;
    private final SubscriptionService subscriptionService;
    private final Clock clock;

    public record Bucket(
            LocalDate start,
            long orders,
            BigDecimal ordersRevenue,
            long rentals,
            BigDecimal rentalsRevenue,
            long newUsers,
            long newProviders) {}

    /** One part of a breakdown: a stable key (for colour), a display label, a count and an amount. */
    public record Part(String key, String label, long count, BigDecimal amount) {}

    public record Trends(
            LocalDate from,
            LocalDate to,
            String granularity,
            List<Bucket> buckets,
            List<Part> orderStatus,
            List<Part> paymentMethods,
            List<Part> topItems,
            List<Part> providerStates) {}

    @Transactional(readOnly = true)
    public Trends forPlatform(LocalDate from, LocalDate to) {
        return build(null, from, to);
    }

    @Transactional(readOnly = true)
    public Trends forProvider(Long providerId, LocalDate from, LocalDate to) {
        if (providerId == null) throw new IllegalArgumentException("providerId required");
        return build(providerId, from, to);
    }

    private Trends build(Long providerId, LocalDate fromRaw, LocalDate toRaw) {
        LocalDate to = toRaw == null ? LocalDate.now(clock.withZone(ZoneOffset.UTC)) : toRaw;
        LocalDate from = fromRaw == null ? to.minusDays(30) : fromRaw;
        if (from.isAfter(to)) {
            LocalDate t = from;
            from = to;
            to = t;
        }
        Instant fromTs = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toTs = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        boolean weekly = days > MAX_DAILY_BUCKETS;
        Map<LocalDate, MutableBucket> buckets = emptyBuckets(from, to, weekly);

        boolean platform = providerId == null;
        Map<String, long[]> statusCounts = new LinkedHashMap<>();
        for (String s : List.of("PENDING_PAYMENT", "PAID", "COLLECTED", "CANCELLED")) statusCounts.put(s, new long[1]);
        Map<Long, String> providerNames = new HashMap<>();
        Map<Long, BigDecimal> providerRevenue = new HashMap<>();

        for (Object[] r : rows("SELECT o.createdAt, o.totalAmount, o.status, o.provider.id, o.provider.name FROM Order o",
                "o", providerId, fromTs, toTs)) {
            Instant at = (Instant) r[0];
            BigDecimal amount = nz((BigDecimal) r[1]);
            OrderStatus status = (OrderStatus) r[2];
            MutableBucket b = buckets.get(bucketStart(at, weekly));
            statusCounts.computeIfAbsent(status.name(), k -> new long[1])[0]++;
            if (b != null) b.orders++;
            if (status != OrderStatus.CANCELLED) {
                if (b != null) b.ordersRevenue = b.ordersRevenue.add(amount);
                providerNames.put((Long) r[3], (String) r[4]);
                providerRevenue.merge((Long) r[3], amount, BigDecimal::add);
            }
        }
        for (Object[] r : rows("SELECT b.createdAt, b.totalAmount, b.status, b.provider.id, b.provider.name FROM RentalBooking b",
                "b", providerId, fromTs, toTs)) {
            Instant at = (Instant) r[0];
            BigDecimal amount = nz((BigDecimal) r[1]);
            BookingStatus status = (BookingStatus) r[2];
            MutableBucket b = buckets.get(bucketStart(at, weekly));
            statusCounts.computeIfAbsent(status.name(), k -> new long[1])[0]++;
            if (b != null) b.rentals++;
            if (status != BookingStatus.CANCELLED) {
                if (b != null) b.rentalsRevenue = b.rentalsRevenue.add(amount);
                providerNames.put((Long) r[3], (String) r[4]);
                providerRevenue.merge((Long) r[3], amount, BigDecimal::add);
            }
        }

        if (platform) {
            for (Instant at : instants("SELECT u.createdAt FROM UserAccount u", "u", fromTs, toTs)) {
                MutableBucket b = buckets.get(bucketStart(at, weekly));
                if (b != null) b.newUsers++;
            }
            for (Instant at : instants("SELECT p.createdAt FROM Provider p", "p", fromTs, toTs)) {
                MutableBucket b = buckets.get(bucketStart(at, weekly));
                if (b != null) b.newProviders++;
            }
        }

        List<Part> orderStatus = new ArrayList<>();
        statusCounts.forEach((k, v) -> orderStatus.add(new Part(k, statusLabel(k), v[0], null)));

        List<Part> topItems = platform ? topProviders(providerNames, providerRevenue) : topListings(providerId, fromTs, toTs);

        return new Trends(
                from,
                to,
                weekly ? "week" : "day",
                buckets.values().stream().map(MutableBucket::freeze).toList(),
                orderStatus,
                paymentMethods(providerId, fromTs, toTs),
                topItems,
                platform ? providerStates() : List.of());
    }

    // ---------------------------------------------------------------- breakdowns

    private List<Part> paymentMethods(Long providerId, Instant fromTs, Instant toTs) {
        String jpql = "SELECT p.method, COUNT(p), COALESCE(SUM(p.amount), 0) FROM PaymentRecord p"
                + " WHERE p.createdAt >= :f AND p.createdAt < :t"
                + (providerId != null ? " AND p.provider.id = :pid" : "")
                + " GROUP BY p.method";
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class).setParameter("f", fromTs).setParameter("t", toTs);
        if (providerId != null) q.setParameter("pid", providerId);
        List<Part> out = new ArrayList<>();
        for (Object[] r : q.getResultList()) {
            PaymentMethod m = (PaymentMethod) r[0];
            if (m == null) continue;
            out.add(new Part(m.name(), methodLabel(m), ((Number) r[1]).longValue(), nz((BigDecimal) r[2])));
        }
        out.sort(Comparator.comparing(Part::amount).reversed());
        return out;
    }

    /** A provider's listings ranked by units sold plus rental bookings (cancelled excluded). */
    private List<Part> topListings(Long providerId, Instant fromTs, Instant toTs) {
        Map<Long, String> titles = new HashMap<>();
        Map<Long, Long> units = new HashMap<>();
        List<Object[]> sales = em.createQuery(
                        "SELECT l.id, l.title, SUM(cl.quantity) FROM CartLine cl JOIN cl.order o JOIN cl.listing l"
                                + " WHERE o.createdAt >= :f AND o.createdAt < :t AND o.provider.id = :pid"
                                + " AND o.status <> :cancelled GROUP BY l.id, l.title",
                        Object[].class)
                .setParameter("f", fromTs)
                .setParameter("t", toTs)
                .setParameter("pid", providerId)
                .setParameter("cancelled", OrderStatus.CANCELLED)
                .getResultList();
        for (Object[] r : sales) {
            titles.put((Long) r[0], (String) r[1]);
            units.merge((Long) r[0], ((Number) r[2]).longValue(), Long::sum);
        }
        List<Object[]> rentals = em.createQuery(
                        "SELECT l.id, l.title, COUNT(b) FROM RentalBooking b JOIN b.listing l"
                                + " WHERE b.createdAt >= :f AND b.createdAt < :t AND b.provider.id = :pid"
                                + " AND b.status <> :cancelled GROUP BY l.id, l.title",
                        Object[].class)
                .setParameter("f", fromTs)
                .setParameter("t", toTs)
                .setParameter("pid", providerId)
                .setParameter("cancelled", BookingStatus.CANCELLED)
                .getResultList();
        for (Object[] r : rentals) {
            titles.put((Long) r[0], (String) r[1]);
            units.merge((Long) r[0], ((Number) r[2]).longValue(), Long::sum);
        }
        return units.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .map(e -> new Part("listing-" + e.getKey(), titles.get(e.getKey()), e.getValue(), null))
                .toList();
    }

    private List<Part> topProviders(Map<Long, String> names, Map<Long, BigDecimal> revenue) {
        return revenue.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(TOP_N)
                .map(e -> new Part("provider-" + e.getKey(), names.get(e.getKey()), 0, e.getValue()))
                .toList();
    }

    /** Every provider in exactly one state, so the parts add up to the provider count. */
    private List<Part> providerStates() {
        Instant now = Instant.now(clock);
        long trial = 0, basic = 0, premium = 0, lapsed = 0, inactive = 0;
        for (Provider p : providerRepository.findAll()) {
            if (p.getStatus() != ProviderStatus.ACTIVE) {
                inactive++;
                continue;
            }
            Optional<Subscription> paid = subscriptionService.currentActive(p.getId());
            if (paid.isPresent()) {
                if (paid.get().getPlan() != null && "PREMIUM".equals(paid.get().getPlan().name())) premium++;
                else basic++;
            } else if (ProviderTrialSupport.isOnTrial(p, now)) {
                trial++;
            } else {
                lapsed++;
            }
        }
        return List.of(
                new Part("TRIAL", "On free trial", trial, null),
                new Part("BASIC", "Basic plan", basic, null),
                new Part("PREMIUM", "Premium plan", premium, null),
                new Part("LAPSED", "Trial ended, no plan", lapsed, null),
                new Part("INACTIVE", "Pending or suspended", inactive, null));
    }

    // ---------------------------------------------------------------- helpers

    private List<Object[]> rows(String select, String alias, Long providerId, Instant fromTs, Instant toTs) {
        String jpql = select + " WHERE " + alias + ".createdAt >= :f AND " + alias + ".createdAt < :t"
                + (providerId != null ? " AND " + alias + ".provider.id = :pid" : "");
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class).setParameter("f", fromTs).setParameter("t", toTs);
        if (providerId != null) q.setParameter("pid", providerId);
        return q.getResultList();
    }

    private List<Instant> instants(String select, String alias, Instant fromTs, Instant toTs) {
        return em.createQuery(
                        select + " WHERE " + alias + ".createdAt >= :f AND " + alias + ".createdAt < :t", Instant.class)
                .setParameter("f", fromTs)
                .setParameter("t", toTs)
                .getResultList();
    }

    static Map<LocalDate, MutableBucket> emptyBuckets(LocalDate from, LocalDate to, boolean weekly) {
        Map<LocalDate, MutableBucket> out = new LinkedHashMap<>();
        LocalDate cursor = weekly ? from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : from;
        while (!cursor.isAfter(to)) {
            out.put(cursor, new MutableBucket(cursor));
            cursor = weekly ? cursor.plusWeeks(1) : cursor.plusDays(1);
        }
        return out;
    }

    static LocalDate bucketStart(Instant at, boolean weekly) {
        if (at == null) return null;
        LocalDate d = LocalDate.ofInstant(at, ZoneOffset.UTC);
        return weekly ? d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : d;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static String statusLabel(String key) {
        return switch (key) {
            case "PENDING_PAYMENT" -> "Awaiting payment";
            case "PAID" -> "Paid";
            case "COLLECTED" -> "Collected";
            case "CANCELLED" -> "Cancelled";
            default -> key.charAt(0) + key.substring(1).toLowerCase().replace('_', ' ');
        };
    }

    static String methodLabel(PaymentMethod m) {
        return switch (m) {
            case CASH -> "Cash";
            case EFT -> "EFT (bank transfer)";
            case PEACH -> "Card / Instant EFT";
            case BOTH -> "Cash or EFT";
        };
    }

    static final class MutableBucket {
        final LocalDate start;
        long orders;
        BigDecimal ordersRevenue = BigDecimal.ZERO;
        long rentals;
        BigDecimal rentalsRevenue = BigDecimal.ZERO;
        long newUsers;
        long newProviders;

        MutableBucket(LocalDate start) {
            this.start = start;
        }

        Bucket freeze() {
            return new Bucket(start, orders, ordersRevenue, rentals, rentalsRevenue, newUsers, newProviders);
        }
    }
}
