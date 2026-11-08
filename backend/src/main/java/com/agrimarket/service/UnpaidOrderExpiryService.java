package com.agrimarket.service;

import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.RentalBookingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Releases orders and rental bookings that were never paid, so their stock and rental dates are not
 * held forever. A released order is cancelled through the normal seller cancel path: reserved stock
 * is returned, the payment record is closed, and buyer and seller are notified.
 *
 * <p>How long an unpaid order is held depends on how the buyer chose to pay:
 * <ul>
 *   <li>Card / Instant EFT (Peach): {@code app.orders.unpaid-expiry.card-minutes} (default 60). The buyer
 *       left the payment page; Peach's own checkout session has expired by then.</li>
 *   <li>EFT bank transfer: {@code eft-hours} (default 72), time for the transfer to clear.</li>
 *   <li>Cash: {@code cash-days} (default 14). Cash is paid at collection, so this is long.</li>
 *   <li>Any unpaid rental is also released once its rental period has ended.</li>
 * </ul>
 * A value of 0 turns that rule off. Paid, collected and cancelled orders are never touched.
 */
@Service
public class UnpaidOrderExpiryService {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiryService.class);

    private final OrderRepository orderRepository;
    private final RentalBookingRepository rentalBookingRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final OrderManagementService orderManagementService;
    private final Clock clock;
    private final boolean enabled;
    private final Duration cardHold;
    private final Duration eftHold;
    private final Duration cashHold;

    public UnpaidOrderExpiryService(
            OrderRepository orderRepository,
            RentalBookingRepository rentalBookingRepository,
            PaymentRecordRepository paymentRecordRepository,
            OrderManagementService orderManagementService,
            Clock clock,
            @Value("${app.orders.unpaid-expiry.enabled:true}") boolean enabled,
            @Value("${app.orders.unpaid-expiry.card-minutes:60}") long cardMinutes,
            @Value("${app.orders.unpaid-expiry.eft-hours:72}") long eftHours,
            @Value("${app.orders.unpaid-expiry.cash-days:14}") long cashDays) {
        this.orderRepository = orderRepository;
        this.rentalBookingRepository = rentalBookingRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.orderManagementService = orderManagementService;
        this.clock = clock;
        this.enabled = enabled;
        this.cardHold = Duration.ofMinutes(Math.max(0, cardMinutes));
        this.eftHold = Duration.ofHours(Math.max(0, eftHours));
        this.cashHold = Duration.ofDays(Math.max(0, cashDays));
    }

    /** Result of one run, for logs and tests. */
    public record Result(int ordersReleased, int rentalsReleased) {}

    @Scheduled(
            initialDelayString = "${app.orders.unpaid-expiry.initial-delay-ms:120000}",
            fixedDelayString = "${app.orders.unpaid-expiry.check-ms:600000}")
    public void scheduledRun() {
        if (!enabled) return;
        try {
            Result r = releaseExpired();
            if (r.ordersReleased() + r.rentalsReleased() > 0) {
                log.info("Released {} unpaid order(s) and {} unpaid rental booking(s)", r.ordersReleased(), r.rentalsReleased());
            }
        } catch (Exception e) {
            log.warn("Unpaid order release run failed: {}", e.getMessage(), e);
        }
    }

    /** Release everything that is past its hold time. Each item is handled on its own, so one failure doesn't stop the rest. */
    public Result releaseExpired() {
        Instant now = Instant.now(clock);
        Duration shortest = shortestHold();
        int orders = 0;
        int rentals = 0;

        if (shortest != null) {
            for (Order o : orderRepository.findAllByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, now.minus(shortest))) {
                Duration hold = holdFor(paymentRecordRepository.findByOrder_Id(o.getId()).map(PaymentRecord::getMethod).orElse(null));
                if (hold != null && o.getCreatedAt() != null && o.getCreatedAt().isBefore(now.minus(hold)) && releaseOrder(o)) {
                    orders++;
                }
            }
        }

        // Rentals: past their hold time, or their rental period has ended. One pass, no duplicates.
        Map<Long, RentalBooking> due = new LinkedHashMap<>();
        for (RentalBooking b : rentalBookingRepository.findAllByStatusAndEndAtBefore(BookingStatus.PENDING_PAYMENT, now)) {
            due.put(b.getId(), b);
        }
        if (shortest != null) {
            for (RentalBooking b : rentalBookingRepository.findAllByStatusAndCreatedAtBefore(BookingStatus.PENDING_PAYMENT, now.minus(shortest))) {
                Duration hold = holdFor(paymentRecordRepository.findByRentalBooking_Id(b.getId()).map(PaymentRecord::getMethod).orElse(null));
                if (hold != null && b.getCreatedAt() != null && b.getCreatedAt().isBefore(now.minus(hold))) {
                    due.putIfAbsent(b.getId(), b);
                }
            }
        }
        for (RentalBooking b : due.values()) {
            if (releaseRental(b)) rentals++;
        }
        return new Result(orders, rentals);
    }

    private boolean releaseOrder(Order o) {
        try {
            // Re-read inside the cancel transaction: a payment may have just arrived.
            Order fresh = orderRepository.findById(o.getId()).orElse(null);
            if (fresh == null || fresh.getStatus() != OrderStatus.PENDING_PAYMENT) return false;
            orderManagementService.cancelOrder(fresh.getProvider().getId(), fresh.getId());
            return true;
        } catch (Exception e) {
            log.warn("Could not release unpaid order {}: {}", o.getId(), e.getMessage());
            return false;
        }
    }

    private boolean releaseRental(RentalBooking b) {
        try {
            RentalBooking fresh = rentalBookingRepository.findById(b.getId()).orElse(null);
            if (fresh == null || fresh.getStatus() != BookingStatus.PENDING_PAYMENT) return false;
            orderManagementService.updateRentalStatus(fresh.getProvider().getId(), fresh.getId(), BookingStatus.CANCELLED);
            return true;
        } catch (Exception e) {
            log.warn("Could not release unpaid rental booking {}: {}", b.getId(), e.getMessage());
            return false;
        }
    }

    /** Hold time for a payment method, or null when that rule is turned off (0). */
    Duration holdFor(PaymentMethod method) {
        Duration d;
        if (method == PaymentMethod.PEACH) d = cardHold;
        else if (method == PaymentMethod.EFT) d = eftHold;
        else d = cashHold; // CASH, BOTH (cash or EFT on collection) and unknown: the most patient rule
        return d.isZero() ? null : d;
    }

    private Duration shortestHold() {
        Duration min = null;
        for (Duration d : new Duration[] {cardHold, eftHold, cashHold}) {
            if (!d.isZero() && (min == null || d.compareTo(min) < 0)) min = d;
        }
        return min;
    }
}
