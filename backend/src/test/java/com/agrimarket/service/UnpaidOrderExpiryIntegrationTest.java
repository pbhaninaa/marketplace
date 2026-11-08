package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UnpaidOrderExpiryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UnpaidOrderExpiryService expiry;
    @Autowired private OrderManagementService orderManagementService;
    @Autowired private TestFixtures fixtures;
    @Autowired private OrderRepository orderRepository;
    @Autowired private RentalBookingRepository rentalBookingRepository;
    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private ListingRepository listingRepository;

    private Provider seller;
    private Provider otherSeller;
    private Listing hay;
    private Listing tractor;
    private Listing trailer;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Category cat = fixtures.saveCategory("Farm", "farm-" + u);
        seller = fixtures.saveActiveProvider("Seller A", "seller-a-" + u);
        otherSeller = fixtures.saveActiveProvider("Seller B", "seller-b-" + u);
        hay = fixtures.saveSaleListing(seller, cat, "Hay", new BigDecimal("50"), 50);
        tractor = fixtures.saveRentListing(seller, cat, "Tractor", new BigDecimal("500"));
        trailer = fixtures.saveRentListing(otherSeller, cat, "Trailer", new BigDecimal("300"));
    }

    private Order order(PaymentMethod method, Instant createdAt, String session, String checkoutRef) {
        Order o = new Order();
        o.setProvider(seller);
        o.setGuestName("Guest");
        o.setGuestEmail("guest@farm.test");
        o.setGuestPhone("0820000000");
        o.setDeliveryOrPickup("PICKUP");
        o.setStatus(OrderStatus.PENDING_PAYMENT);
        o.setPaymentStatus(PaymentStatus.PENDING);
        o.setTotalAmount(new BigDecimal("100.00"));
        o.setCreatedAt(createdAt);
        o.setSessionKey(session);
        o.setCheckoutRef(checkoutRef);
        o.setVerificationCode(UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        CartLine line = new CartLine();
        line.setOrder(o);
        line.setListing(hay);
        line.setQuantity(2);
        o.getLines().add(line);
        ListingStock.addReservation(hay, 2);
        listingRepository.save(hay);
        o = orderRepository.save(o);
        pay(o, null, method);
        return o;
    }

    private RentalBooking rental(Provider p, Listing l, PaymentMethod method, Instant createdAt, Instant start, Instant end,
            String session, String checkoutRef) {
        RentalBooking b = new RentalBooking();
        b.setProvider(p);
        b.setListing(l);
        b.setGuestName("Guest");
        b.setGuestEmail("guest@farm.test");
        b.setGuestPhone("0820000000");
        b.setDeliveryOrPickup("PICKUP");
        b.setStartAt(start);
        b.setEndAt(end);
        b.setTotalAmount(new BigDecimal("500.00"));
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setCreatedAt(createdAt);
        b.setSessionKey(session);
        b.setCheckoutRef(checkoutRef);
        b.setVerificationCode(UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        b = rentalBookingRepository.save(b);
        pay(null, b, method);
        return b;
    }

    private void pay(Order o, RentalBooking b, PaymentMethod method) {
        PaymentRecord pr = new PaymentRecord();
        pr.setProvider(o != null ? o.getProvider() : b.getProvider());
        pr.setOrder(o);
        pr.setRentalBooking(b);
        pr.setMethod(method);
        pr.setStatus(PaymentRecordStatus.PENDING_PAYMENT);
        pr.setAmount(o != null ? o.getTotalAmount() : b.getTotalAmount());
        paymentRecordRepository.save(pr);
    }

    private OrderStatus statusOf(Order o) {
        return orderRepository.findById(o.getId()).orElseThrow().getStatus();
    }

    private BookingStatus statusOf(RentalBooking b) {
        return rentalBookingRepository.findById(b.getId()).orElseThrow().getStatus();
    }

    @Test
    void unpaidOrdersAreReleasedAfterTheirPaymentMethodsHoldTime() {
        Instant now = Instant.now();
        Order cardOld = order(PaymentMethod.PEACH, now.minus(2, ChronoUnit.HOURS), "s1", null);
        Order cardNew = order(PaymentMethod.PEACH, now.minus(10, ChronoUnit.MINUTES), "s2", null);
        Order eftOld = order(PaymentMethod.EFT, now.minus(4, ChronoUnit.DAYS), "s3", null);
        Order eftNew = order(PaymentMethod.EFT, now.minus(1, ChronoUnit.DAYS), "s4", null);
        Order cashOld = order(PaymentMethod.CASH, now.minus(15, ChronoUnit.DAYS), "s5", null);
        Order cashNew = order(PaymentMethod.CASH, now.minus(5, ChronoUnit.DAYS), "s6", null);
        int reservedBefore = listingRepository.findById(hay.getId()).orElseThrow().getReservedStock();

        expiry.releaseExpired();

        assertThat(statusOf(cardOld)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusOf(cardNew)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(statusOf(eftOld)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusOf(eftNew)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(statusOf(cashOld)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusOf(cashNew)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        // Three released orders of 2 units each give their reserved stock back.
        assertThat(listingRepository.findById(hay.getId()).orElseThrow().getReservedStock()).isEqualTo(reservedBefore - 6);
    }

    @Test
    void paidOrdersAreNeverReleased() {
        Order paid = order(PaymentMethod.PEACH, Instant.now().minus(3, ChronoUnit.HOURS), "s7", null);
        Order fresh = orderRepository.findById(paid.getId()).orElseThrow();
        fresh.setStatus(OrderStatus.PAID);
        orderRepository.save(fresh);
        expiry.releaseExpired();
        assertThat(statusOf(paid)).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void unpaidRentalsFreeTheirDatesAfterTheHoldTimeOrOnceTheRentalHasEnded() {
        Instant now = Instant.now();
        Instant future = now.plus(10, ChronoUnit.DAYS);
        RentalBooking cardOld = rental(seller, tractor, PaymentMethod.PEACH, now.minus(2, ChronoUnit.HOURS),
                future, future.plus(1, ChronoUnit.DAYS), "r1", null);
        RentalBooking cashFuture = rental(seller, tractor, PaymentMethod.CASH, now.minus(1, ChronoUnit.DAYS),
                future.plus(5, ChronoUnit.DAYS), future.plus(6, ChronoUnit.DAYS), "r2", null);
        RentalBooking cashEnded = rental(seller, tractor, PaymentMethod.CASH, now.minus(3, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), "r3", null);

        expiry.releaseExpired();

        assertThat(statusOf(cardOld)).isEqualTo(BookingStatus.CANCELLED);
        assertThat(statusOf(cashFuture)).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(statusOf(cashEnded)).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancellingOneSellersOrderNeverTouchesAnotherSellersBookingFromTheSameCart() {
        // One browser cart (same session key), two sellers, two separate checkouts.
        String session = "shared-cart-" + UUID.randomUUID();
        Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
        Order sellerAOrder = order(PaymentMethod.CASH, Instant.now(), session, "checkout-a");
        RentalBooking sellerARental = rental(seller, tractor, PaymentMethod.CASH, Instant.now(),
                future, future.plus(1, ChronoUnit.DAYS), session, "checkout-a");
        RentalBooking sellerBRental = rental(otherSeller, trailer, PaymentMethod.CASH, Instant.now(),
                future, future.plus(1, ChronoUnit.DAYS), session, "checkout-b");

        orderManagementService.updateOrderStatus(seller.getId(), sellerAOrder.getId(), OrderStatus.CANCELLED);

        assertThat(statusOf(sellerARental)).isEqualTo(BookingStatus.CANCELLED); // same checkout: kept in step
        assertThat(statusOf(sellerBRental)).isEqualTo(BookingStatus.PENDING_PAYMENT); // other seller: untouched
    }

    @Test
    void cancellingARentalWithSeveralOrdersInTheSameSessionDoesNotFail() {
        // Before: a rental status change looked up "the" order for the session and failed when there were two.
        String session = "repeat-cart-" + UUID.randomUUID();
        Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
        order(PaymentMethod.CASH, Instant.now(), session, "checkout-1");
        order(PaymentMethod.CASH, Instant.now(), session, "checkout-2");
        RentalBooking r = rental(seller, tractor, PaymentMethod.CASH, Instant.now(),
                future, future.plus(1, ChronoUnit.DAYS), session, "checkout-3");

        orderManagementService.updateRentalStatus(seller.getId(), r.getId(), BookingStatus.CANCELLED);

        assertThat(statusOf(r)).isEqualTo(BookingStatus.CANCELLED);
    }
}
