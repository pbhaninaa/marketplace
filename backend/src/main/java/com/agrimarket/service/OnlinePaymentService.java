package com.agrimarket.service;

import com.agrimarket.api.dto.PeachStatusResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionActivationIntent;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.SubscriptionActivationIntentRepository;
import com.agrimarket.repo.SubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gateway-independent effects of an online payment, shared by Peach and PayFast: marking orders /
 * rentals paid (with PAID_AFTER_CANCEL handling and seller settlement), releasing unpaid rentals when
 * the buyer cancels, activating a provider subscription, and the return-page status lookup.
 * Callers have already verified the notification and locked the records.
 */
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

    /** Peach "cancelled by user" result code prefix. */
    private static final String PEACH_CANCELLED_PREFIX = "100.396.101";
    /** PayFast payment_status for a cancelled payment (stored in gatewayResultCode). */
    public static final String PAYFAST_CANCELLED = "CANCELLED";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OnlinePaymentService.class);

    private final PaymentRecordRepository paymentRecordRepository;
    private final SubscriptionActivationIntentRepository intentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final PurchaseInventoryService purchaseInventoryService;
    private final AppNotificationService appNotificationService;
    private final ReferralService referralService;
    private final SellerSettlementService sellerSettlementService;

    /**
     * Marks every payment of one checkout PAID, moves its order / rental to PAID and records the
     * seller settlement. {@code gatewayTransactionId} (Peach checkoutId / PayFast pf_payment_id) is
     * stored in gatewayCheckoutId when given.
     */
    public void confirmCheckoutPaid(List<PaymentRecord> payments, String gatewayTransactionId) {
        for (PaymentRecord pr : payments) {
            if (pr.getStatus() == PaymentRecordStatus.PAID) {
                continue; // idempotent
            }
            pr.setStatus(PaymentRecordStatus.PAID);
            pr.setPaidAt(Instant.now());
            if (gatewayTransactionId != null && !gatewayTransactionId.isBlank()) {
                pr.setGatewayCheckoutId(gatewayTransactionId.trim());
            }
            boolean cancelled = false;
            if (pr.getOrder() != null) {
                cancelled |= markOrderPaid(pr.getOrder());
            }
            if (pr.getRentalBooking() != null) {
                cancelled |= markRentalPaid(pr.getRentalBooking());
            }
            sellerSettlementService.recordConfirmedPayment(pr, cancelled);
            paymentRecordRepository.save(pr);
        }
    }

    /** The buyer cancelled on the gateway page: free the rental slots this checkout was holding. */
    public void releaseUnpaidRentals(List<PaymentRecord> payments) {
        for (PaymentRecord payment : payments) {
            releaseUnpaidRental(payment.getRentalBooking());
        }
    }

    /**
     * Activates the provider's subscription for a paid quote (shared by Peach and PayFast). The caller
     * has checked the intent is unused and the amount matches.
     */
    public void activateSubscription(SubscriptionActivationIntent intent, String gatewayTransactionId, String paidVia) {
        Provider provider = intent.getProvider();
        subscriptionRepository
                .findActiveForProviderOrderByExpiresAtDesc(provider.getId(), SubscriptionStatus.ACTIVE, Instant.now())
                .forEach(existing -> existing.setStatus(SubscriptionStatus.CANCELLED));

        Subscription sub = new Subscription();
        sub.setProvider(provider);
        sub.setPlan(intent.getPlan());
        sub.setBillingCycle(intent.getBillingCycle());
        sub.setStatus(SubscriptionStatus.PENDING_VERIFICATION);
        Instant now = Instant.now();
        sub.setExpiresAt(now.plus(30, ChronoUnit.DAYS));
        sub.setCreatedAt(now);
        sub.setAmountDue(intent.getAmountDue());
        sub.setPaymentReference(intent.getPaymentReference());
        sub = subscriptionRepository.save(sub);

        if (gatewayTransactionId != null && !gatewayTransactionId.isBlank()) {
            intent.setGatewayCheckoutId(gatewayTransactionId.trim());
        }
        intent.setUsed(true);
        intentRepository.save(intent);

        subscriptionService.activateFromVerifiedPeachCallback(sub);
        try {
            // Referral commission on the referred provider's first paid subscription (idempotent).
            referralService.recordQualifyingPayment(
                    provider.getId(), sub.getId(), intent.getAmountDue(), intent.getGatewayMerchantRef());
        } catch (Exception e) {
            log.warn("Referral commission not recorded for provider {}: {}", provider.getId(), e.getMessage());
        }
        try {
            appNotificationService.notifySubscriptionDecision(provider, true, "Paid via " + paidVia + " online checkout");
        } catch (Exception ignored) {
            // Never fail activation due to notifications.
        }
    }

    /** Return-page status, looked up by the unguessable gateway reference (any gateway). */
    @Transactional(readOnly = true)
    public PeachStatusResponse getStatusByRef(String ref) {
        String cleaned = ref == null ? null : ref.trim();
        if (cleaned == null || cleaned.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_REF", "Missing reference");
        }

        List<PaymentRecord> payments = paymentRecordRepository.findAllByGatewayMerchantRef(cleaned);
        if (!payments.isEmpty()) {
            boolean allPaid = payments.stream().allMatch(p -> p.getStatus() == PaymentRecordStatus.PAID);
            boolean cancelled = payments.stream()
                    .map(PaymentRecord::getGatewayResultCode)
                    .anyMatch(OnlinePaymentService::isCancelledResultCode);
            List<Long> orderIds = new ArrayList<>();
            List<Long> rentalIds = new ArrayList<>();
            List<String> codes = new ArrayList<>();
            for (PaymentRecord pr : payments) {
                if (pr.getOrder() != null) {
                    orderIds.add(pr.getOrder().getId());
                    codes.add(pr.getOrder().getVerificationCode());
                }
                if (pr.getRentalBooking() != null) {
                    rentalIds.add(pr.getRentalBooking().getId());
                    codes.add(pr.getRentalBooking().getVerificationCode());
                }
            }
            return new PeachStatusResponse(
                    allPaid ? "PAID" : (cancelled ? "CANCELLED" : "PENDING_PAYMENT"), orderIds, rentalIds, codes);
        }

        Optional<SubscriptionActivationIntent> intentOpt = intentRepository.findByGatewayMerchantRef(cleaned);
        if (intentOpt.isPresent()) {
            SubscriptionActivationIntent intent = intentOpt.get();
            String status = intent.isUsed()
                    ? "PAID"
                    : (isCancelledResultCode(intent.getGatewayResultCode()) ? "CANCELLED" : "PENDING_PAYMENT");
            return new PeachStatusResponse(status, List.of(), List.of(), List.of());
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_REF", "Payment reference not found");
    }

    public static boolean isCancelledResultCode(String code) {
        return code != null && (code.startsWith(PEACH_CANCELLED_PREFIX) || PAYFAST_CANCELLED.equals(code));
    }

    /** @return true when the order was already cancelled (money taken for an order that won't go ahead). */
    private boolean markOrderPaid(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("PAID_AFTER_CANCEL: online payment confirmed for cancelled order {}; refund the buyer", order.getId());
            return true;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false; // already paid/collected: nothing to do (idempotent)
        }
        purchaseInventoryService.finalizePaidPurchase(order);
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.PAID);
        try {
            appNotificationService.notifyOrderStatus(order);
        } catch (Exception ignored) {
            // Never fail payment confirmation due to notifications.
        }
        return false;
    }

    /** @return true when the booking was already cancelled. */
    private boolean markRentalPaid(RentalBooking rental) {
        if (rental.getStatus() == BookingStatus.CANCELLED) {
            log.warn("PAID_AFTER_CANCEL: online payment confirmed for cancelled rental booking {}; refund the buyer", rental.getId());
            return true;
        }
        if (rental.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return false;
        }
        rental.setStatus(BookingStatus.PAID);
        try {
            appNotificationService.notifyRentalStatus(rental);
        } catch (Exception ignored) {
            // Never fail payment confirmation due to notifications.
        }
        return false;
    }

    /** Cancel a rental that was never paid, freeing its time slot. No-op for paid/collected/cancelled. */
    private void releaseUnpaidRental(RentalBooking rental) {
        if (rental == null || rental.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return;
        }
        rental.setStatus(BookingStatus.CANCELLED);
        try {
            appNotificationService.notifyRentalStatus(rental);
        } catch (Exception ignored) {
            // Never fail notification handling due to notifications.
        }
    }
}
