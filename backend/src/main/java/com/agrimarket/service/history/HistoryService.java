package com.agrimarket.service.history;

import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.RentalBooking;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filterable, server-paginated order and payment history for admins, providers and clients, plus the
 * full (unpaged) row set used by CSV/Excel exports.
 */
@Service
public class HistoryService {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int MAX_EXPORT_ROWS = 50_000;
    private static final int EXPORT_CHUNK = 500;

    @PersistenceContext
    private EntityManager em;

    // ------------------------------------------------------------------ DTOs

    public record OrderRow(
            Long id,
            Instant createdAt,
            Long providerId,
            String providerName,
            String customerName,
            String customerEmail,
            String customerPhone,
            String status,
            String paymentStatus,
            String paymentMethod,
            BigDecimal totalAmount,
            BigDecimal deliveryFee,
            String fulfilment,
            int itemCount,
            String items,
            /** The client's own order reference (invoice lookup) — only on client-facing rows. */
            String reference) {}

    public record PaymentRow(
            Long id,
            Instant createdAt,
            Long providerId,
            String providerName,
            String kind,
            Long orderId,
            Long rentalId,
            String customerName,
            String customerEmail,
            String method,
            String status,
            BigDecimal amount) {}

    public record MethodTotal(String method, long count, BigDecimal amount) {}

    public record Summary(
            long count, BigDecimal totalAmount, long paidCount, BigDecimal paidAmount, List<MethodTotal> byMethod) {}

    public record HistoryPage<T>(
            List<T> content, int page, int size, long totalElements, int totalPages, Summary summary) {}

    // ------------------------------------------------------------------ Orders

    @Transactional(readOnly = true)
    public HistoryPage<OrderRow> orders(HistoryScope scope, HistoryFilter rawFilter, int page, int size) {
        HistoryFilter f = scope.apply(rawFilter);
        int safeSize = clampSize(size);
        int safePage = Math.max(0, page);
        List<OrderRow> rows = orderRows(scope, f, safePage * safeSize, safeSize);
        Summary summary = orderSummary(scope, f);
        return new HistoryPage<>(rows, safePage, safeSize, summary.count(), totalPages(summary.count(), safeSize), summary);
    }

    @Transactional(readOnly = true)
    public List<OrderRow> allOrders(HistoryScope scope, HistoryFilter rawFilter) {
        HistoryFilter f = scope.apply(rawFilter);
        List<OrderRow> out = new ArrayList<>();
        for (int offset = 0; offset < MAX_EXPORT_ROWS; offset += EXPORT_CHUNK) {
            List<OrderRow> chunk = orderRows(scope, f, offset, Math.min(EXPORT_CHUNK, MAX_EXPORT_ROWS - offset));
            out.addAll(chunk);
            em.clear();
            if (chunk.size() < EXPORT_CHUNK) break;
        }
        return out;
    }

    private List<OrderRow> orderRows(HistoryScope scope, HistoryFilter f, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Order> q = cb.createQuery(Order.class);
        Root<Order> o = q.from(Order.class);
        o.fetch("provider", JoinType.INNER);
        q.select(o)
                .where(orderPredicates(cb, q, o, scope, f))
                .orderBy(cb.desc(o.get("createdAt")), cb.desc(o.get("id")));
        List<Order> orders = em.createQuery(q).setFirstResult(offset).setMaxResults(limit).getResultList();
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(Order::getId).toList();
        Map<Long, List<CartLine>> lines = loadLines(ids);
        Map<Long, PaymentMethod> methods = loadOrderMethods(ids);
        return orders.stream()
                .map(order -> toOrderRow(order, lines.getOrDefault(order.getId(), List.of()), methods.get(order.getId()), scope))
                .toList();
    }

    private Summary orderSummary(HistoryScope scope, HistoryFilter f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Order> o = q.from(Order.class);
        Expression<BigDecimal> amount = o.get("totalAmount");
        Predicate paid = cb.equal(o.get("paymentStatus"), PaymentStatus.PAID);
        q.multiselect(
                        cb.count(o),
                        cb.coalesce(cb.sum(amount), BigDecimal.ZERO),
                        cb.sum(cb.<Long>selectCase().when(paid, 1L).otherwise(0L)),
                        cb.coalesce(
                                cb.sum(cb.<BigDecimal>selectCase().when(paid, amount).otherwise(BigDecimal.ZERO)),
                                BigDecimal.ZERO))
                .where(orderPredicates(cb, q, o, scope, f));
        Tuple t = em.createQuery(q).getSingleResult();
        long count = asLong(t.get(0));
        return new Summary(count, asDecimal(t.get(1)), asLong(t.get(2)), asDecimal(t.get(3)), List.of());
    }

    private Predicate[] orderPredicates(
            CriteriaBuilder cb, CriteriaQuery<?> q, Root<Order> o, HistoryScope scope, HistoryFilter f) {
        List<Predicate> p = new ArrayList<>();
        if (f.providerId() != null) {
            p.add(cb.equal(o.get("provider").get("id"), f.providerId()));
        }
        if (scope.clientTarget() != null) {
            p.add(customerMatches(cb, o, scope.clientTarget()));
        }
        if (f.status() != null) {
            p.add(cb.equal(o.get("status"), f.status()));
        }
        if (f.orderPaymentStatus() != null) {
            p.add(cb.equal(o.get("paymentStatus"), f.orderPaymentStatus()));
        }
        addDateRange(cb, p, o.get("createdAt"), f);
        if (f.method() != null) {
            Subquery<Long> sq = q.subquery(Long.class);
            Root<PaymentRecord> pr = sq.from(PaymentRecord.class);
            sq.select(pr.get("id")).where(cb.equal(pr.get("order"), o), cb.equal(pr.get("method"), f.method()));
            p.add(cb.exists(sq));
        }
        if (f.q() != null) {
            p.add(textMatches(cb, List.of(o), o.get("id"), f.q()));
        }
        return p.toArray(Predicate[]::new);
    }

    private Map<Long, List<CartLine>> loadLines(Collection<Long> orderIds) {
        List<CartLine> lines = em.createQuery(
                        "SELECT l FROM CartLine l JOIN FETCH l.listing WHERE l.order.id IN :ids ORDER BY l.id",
                        CartLine.class)
                .setParameter("ids", orderIds)
                .getResultList();
        return lines.stream().collect(Collectors.groupingBy(l -> l.getOrder().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, PaymentMethod> loadOrderMethods(Collection<Long> orderIds) {
        List<Object[]> rows = em.createQuery(
                        "SELECT pr.order.id, pr.method FROM PaymentRecord pr WHERE pr.order.id IN :ids", Object[].class)
                .setParameter("ids", orderIds)
                .getResultList();
        Map<Long, PaymentMethod> out = new HashMap<>();
        for (Object[] r : rows) {
            out.putIfAbsent((Long) r[0], (PaymentMethod) r[1]);
        }
        return out;
    }

    private OrderRow toOrderRow(Order o, List<CartLine> lines, PaymentMethod method, HistoryScope scope) {
        Provider provider = o.getProvider();
        int itemCount = lines.stream().mapToInt(CartLine::getQuantity).sum();
        String items = lines.stream()
                .map(l -> l.getQuantity() + " x " + (l.getListing() != null ? l.getListing().getTitle() : "item"))
                .collect(Collectors.joining(", "));
        return new OrderRow(
                o.getId(),
                o.getCreatedAt(),
                provider != null ? provider.getId() : null,
                provider != null ? provider.getName() : null,
                o.getGuestName(),
                o.getGuestEmail(),
                o.getGuestPhone(),
                o.getStatus() != null ? o.getStatus().name() : null,
                o.getPaymentStatus() != null ? o.getPaymentStatus().name() : null,
                method != null ? method.name() : null,
                o.getTotalAmount(),
                o.getDeliveryFee(),
                o.getDeliveryOrPickup(),
                itemCount,
                items,
                // Providers never see the meetup code (guest must speak it); the client may see their own.
                scope.clientView() ? o.getVerificationCode() : null);
    }

    // ------------------------------------------------------------------ Payments

    @Transactional(readOnly = true)
    public HistoryPage<PaymentRow> payments(HistoryScope scope, HistoryFilter rawFilter, int page, int size) {
        HistoryFilter f = scope.apply(rawFilter);
        int safeSize = clampSize(size);
        int safePage = Math.max(0, page);
        List<PaymentRow> rows = paymentRows(scope, f, safePage * safeSize, safeSize);
        Summary summary = paymentSummary(scope, f);
        return new HistoryPage<>(rows, safePage, safeSize, summary.count(), totalPages(summary.count(), safeSize), summary);
    }

    @Transactional(readOnly = true)
    public List<PaymentRow> allPayments(HistoryScope scope, HistoryFilter rawFilter) {
        HistoryFilter f = scope.apply(rawFilter);
        List<PaymentRow> out = new ArrayList<>();
        for (int offset = 0; offset < MAX_EXPORT_ROWS; offset += EXPORT_CHUNK) {
            List<PaymentRow> chunk = paymentRows(scope, f, offset, Math.min(EXPORT_CHUNK, MAX_EXPORT_ROWS - offset));
            out.addAll(chunk);
            em.clear();
            if (chunk.size() < EXPORT_CHUNK) break;
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<PaymentRow> paymentRows(HistoryScope scope, HistoryFilter f, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PaymentRecord> q = cb.createQuery(PaymentRecord.class);
        Root<PaymentRecord> p = q.from(PaymentRecord.class);
        p.fetch("provider", JoinType.INNER);
        Join<PaymentRecord, Order> order = (Join<PaymentRecord, Order>) p.<PaymentRecord, Order>fetch("order", JoinType.LEFT);
        Join<PaymentRecord, RentalBooking> rental =
                (Join<PaymentRecord, RentalBooking>) p.<PaymentRecord, RentalBooking>fetch("rentalBooking", JoinType.LEFT);
        q.select(p)
                .where(paymentPredicates(cb, p, order, rental, scope, f))
                .orderBy(cb.desc(p.get("createdAt")), cb.desc(p.get("id")));
        return em.createQuery(q).setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(this::toPaymentRow)
                .toList();
    }

    private Summary paymentSummary(HistoryScope scope, HistoryFilter f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<PaymentRecord> p = q.from(PaymentRecord.class);
        Join<PaymentRecord, Order> order = p.join("order", JoinType.LEFT);
        Join<PaymentRecord, RentalBooking> rental = p.join("rentalBooking", JoinType.LEFT);
        Expression<BigDecimal> amount = p.get("amount");
        Predicate paid = cb.equal(p.get("status"), PaymentRecordStatus.PAID);
        q.multiselect(
                        p.get("method"),
                        cb.count(p),
                        cb.coalesce(cb.sum(amount), BigDecimal.ZERO),
                        cb.sum(cb.<Long>selectCase().when(paid, 1L).otherwise(0L)),
                        cb.coalesce(
                                cb.sum(cb.<BigDecimal>selectCase().when(paid, amount).otherwise(BigDecimal.ZERO)),
                                BigDecimal.ZERO))
                .where(paymentPredicates(cb, p, order, rental, scope, f))
                .groupBy(p.get("method"));
        long count = 0;
        long paidCount = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paidTotal = BigDecimal.ZERO;
        List<MethodTotal> byMethod = new ArrayList<>();
        for (Tuple t : em.createQuery(q).getResultList()) {
            PaymentMethod m = (PaymentMethod) t.get(0);
            long c = asLong(t.get(1));
            BigDecimal sum = asDecimal(t.get(2));
            count += c;
            total = total.add(sum);
            paidCount += asLong(t.get(3));
            paidTotal = paidTotal.add(asDecimal(t.get(4)));
            byMethod.add(new MethodTotal(m != null ? m.name() : "UNKNOWN", c, sum));
        }
        byMethod.sort((a, b) -> b.amount().compareTo(a.amount()));
        return new Summary(count, total, paidCount, paidTotal, byMethod);
    }

    private Predicate[] paymentPredicates(
            CriteriaBuilder cb,
            Root<PaymentRecord> p,
            Join<PaymentRecord, Order> order,
            Join<PaymentRecord, RentalBooking> rental,
            HistoryScope scope,
            HistoryFilter f) {
        List<Predicate> preds = new ArrayList<>();
        if (f.providerId() != null) {
            preds.add(cb.equal(p.get("provider").get("id"), f.providerId()));
        }
        if (scope.clientTarget() != null) {
            preds.add(cb.or(customerMatches(cb, order, scope.clientTarget()), customerMatches(cb, rental, scope.clientTarget())));
        }
        if (f.paymentRecordStatus() != null) {
            preds.add(cb.equal(p.get("status"), f.paymentRecordStatus()));
        }
        if (f.method() != null) {
            preds.add(cb.equal(p.get("method"), f.method()));
        }
        if ("ORDER".equals(f.kind())) {
            preds.add(cb.isNotNull(order.get("id")));
        } else if ("RENTAL".equals(f.kind())) {
            preds.add(cb.isNotNull(rental.get("id")));
        }
        if (f.status() != null) {
            preds.add(cb.equal(order.get("status"), f.status()));
        }
        addDateRange(cb, preds, p.get("createdAt"), f);
        if (f.q() != null) {
            preds.add(textMatches(cb, List.of(order, rental), p.get("id"), f.q()));
        }
        return preds.toArray(Predicate[]::new);
    }

    private PaymentRow toPaymentRow(PaymentRecord p) {
        Order o = p.getOrder();
        RentalBooking r = p.getRentalBooking();
        Provider provider = p.getProvider();
        String name = o != null ? o.getGuestName() : r != null ? r.getGuestName() : null;
        String email = o != null ? o.getGuestEmail() : r != null ? r.getGuestEmail() : null;
        return new PaymentRow(
                p.getId(),
                p.getCreatedAt(),
                provider != null ? provider.getId() : null,
                provider != null ? provider.getName() : null,
                r != null && o == null ? "RENTAL" : "ORDER",
                o != null ? o.getId() : null,
                r != null ? r.getId() : null,
                name,
                email,
                p.getMethod() != null ? p.getMethod().name() : null,
                p.getStatus() != null ? p.getStatus().name() : null,
                p.getAmount());
    }

    // ------------------------------------------------------------------ helpers

    private static Predicate customerMatches(CriteriaBuilder cb, From<?, ?> from, String target) {
        return cb.or(
                cb.equal(cb.lower(from.get("guestEmail")), target),
                cb.equal(from.get("guestPhone"), target));
    }

    private static Predicate textMatches(CriteriaBuilder cb, List<From<?, ?>> sources, Expression<Long> id, String raw) {
        String like = "%" + raw.toLowerCase(Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "") + "%";
        List<Predicate> any = new ArrayList<>();
        for (From<?, ?> s : sources) {
            any.add(cb.like(cb.lower(s.get("guestName")), like));
            any.add(cb.like(cb.lower(s.get("guestEmail")), like));
            any.add(cb.like(s.get("guestPhone"), like));
        }
        String digits = raw.replaceFirst("^#", "");
        if (digits.matches("\\d{1,18}")) {
            any.add(cb.equal(id, Long.parseLong(digits)));
        }
        return cb.or(any.toArray(Predicate[]::new));
    }

    private static void addDateRange(CriteriaBuilder cb, List<Predicate> p, Expression<Instant> at, HistoryFilter f) {
        Instant from = f.fromInstant();
        Instant to = f.toInstantExclusive();
        if (from != null) p.add(cb.greaterThanOrEqualTo(at, from));
        if (to != null) p.add(cb.lessThan(at, to));
    }

    private static int clampSize(int size) {
        if (size <= 0) return 25;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static int totalPages(long total, int size) {
        return (int) Math.max(1, (total + size - 1) / size);
    }

    private static long asLong(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    private static BigDecimal asDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString());
    }
}
