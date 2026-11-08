package com.agrimarket.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.Provider;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.service.dashboard.DashboardTrendsService.Part;
import com.agrimarket.service.dashboard.DashboardTrendsService.Trends;
import com.agrimarket.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Uses dates in 2020 so rows created "now" by other tests never fall in range. */
class DashboardTrendsServiceIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate DAY1 = LocalDate.of(2020, 3, 2);
    private static final LocalDate DAY2 = LocalDate.of(2020, 3, 3);

    @Autowired private DashboardTrendsService service;
    @Autowired private TestFixtures fixtures;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRecordRepository paymentRecordRepository;

    private Order order(Provider p, OrderStatus status, String total, LocalDate day, PaymentMethod method) {
        Order o = new Order();
        o.setProvider(p);
        o.setGuestName("Guest");
        o.setGuestEmail("guest@farm.test");
        o.setGuestPhone("0820000000");
        o.setDeliveryOrPickup("Pickup");
        o.setStatus(status);
        o.setPaymentStatus(status == OrderStatus.PAID ? PaymentStatus.PAID : PaymentStatus.PENDING);
        o.setTotalAmount(new BigDecimal(total));
        Instant at = day.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC);
        o.setCreatedAt(at);
        o.setVerificationCode(UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        o = orderRepository.save(o);
        PaymentRecord pr = new PaymentRecord();
        pr.setProvider(p);
        pr.setOrder(o);
        pr.setMethod(method);
        pr.setStatus(PaymentRecordStatus.PENDING_PAYMENT);
        pr.setAmount(new BigDecimal(total));
        pr.setCreatedAt(at);
        paymentRecordRepository.save(pr);
        return o;
    }

    private static Part part(java.util.List<Part> parts, String key) {
        return parts.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void providerTrendsAreDailyScopedAndExcludeCancelledRevenue() {
        String u = UUID.randomUUID().toString().substring(0, 6);
        Provider a = fixtures.saveActiveProvider("Alpha " + u, "alpha-" + u);
        Provider b = fixtures.saveActiveProvider("Beta " + u, "beta-" + u);
        order(a, OrderStatus.PAID, "100.00", DAY1, PaymentMethod.CASH);
        order(a, OrderStatus.CANCELLED, "999.00", DAY1, PaymentMethod.CASH);
        order(a, OrderStatus.PENDING_PAYMENT, "50.00", DAY2, PaymentMethod.EFT);
        order(b, OrderStatus.PAID, "7000.00", DAY2, PaymentMethod.CASH);

        Trends t = service.forProvider(a.getId(), DAY1, DAY2);

        assertThat(t.granularity()).isEqualTo("day");
        assertThat(t.buckets()).hasSize(2);
        assertThat(t.buckets().get(0).orders()).isEqualTo(2);
        assertThat(t.buckets().get(0).ordersRevenue()).isEqualByComparingTo("100.00");
        assertThat(t.buckets().get(1).ordersRevenue()).isEqualByComparingTo("50.00");
        assertThat(part(t.orderStatus(), "CANCELLED").count()).isEqualTo(1);
        assertThat(part(t.orderStatus(), "PAID").count()).isEqualTo(1);
        // Provider B's order and payment never leak into A's charts.
        assertThat(part(t.paymentMethods(), "CASH").amount()).isEqualByComparingTo("1099.00");
        assertThat(t.providerStates()).isEmpty();
    }

    @Test
    void platformTrendsRankProvidersByRevenueAndUseWeeksForLongRanges() {
        String u = UUID.randomUUID().toString().substring(0, 6);
        Provider small = fixtures.saveActiveProvider("Small " + u, "small-" + u);
        Provider big = fixtures.saveActiveProvider("Big " + u, "big-" + u);
        order(small, OrderStatus.PAID, "100.00", DAY1, PaymentMethod.CASH);
        order(big, OrderStatus.PAID, "900.00", DAY2, PaymentMethod.PEACH);

        Trends daily = service.forPlatform(DAY1, DAY2);
        assertThat(daily.topItems().get(0).label()).isEqualTo("Big " + u);
        assertThat(daily.topItems().get(1).label()).isEqualTo("Small " + u);
        assertThat(daily.providerStates()).extracting(Part::key)
                .containsExactly("TRIAL", "BASIC", "PREMIUM", "LAPSED", "INACTIVE");
        long totalProviders = daily.providerStates().stream().mapToLong(Part::count).sum();
        assertThat(totalProviders).isGreaterThanOrEqualTo(2);

        Trends weekly = service.forPlatform(DAY1, DAY1.plusDays(90));
        assertThat(weekly.granularity()).isEqualTo("week");
        assertThat(weekly.buckets().size()).isBetween(13, 15);
        BigDecimal revenue = weekly.buckets().stream()
                .map(DashboardTrendsService.Bucket::ordersRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(revenue).isEqualByComparingTo("1000.00");
    }
}
