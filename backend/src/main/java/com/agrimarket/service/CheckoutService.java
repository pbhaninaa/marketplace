package com.agrimarket.service;

import com.agrimarket.api.dto.GuestCheckoutRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.CartSession;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PeachPaymentMethod;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.CartSessionRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartService cartService;
    private final SubscriptionService subscriptionService;
    private final RentalPricingService rentalPricingService;
    private final RentalBookingRepository rentalBookingRepository;
    private final OrderRepository OrderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final CartSessionRepository cartSessionRepository;
    private final ListingRepository listingRepository;
    private final VerificationCodeService verificationCodeService;
    private final AppNotificationService appNotificationService;
    private final PeachPaymentService peachPaymentService;

    @Transactional
    public Map<String, Object> guestCheckout(String sessionKey, GuestCheckoutRequest req) {
        CartSession cart = cartService.requireCartWithLines(sessionKey);
        Long providerId = cart.getProvider().getId();

        if (cart.getProvider().getStatus() != ProviderStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_INACTIVE", "Provider is not active");
        }
        if (!subscriptionService.hasActiveSubscription(providerId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SUBSCRIPTION_INACTIVE",
                    "This provider cannot accept orders right now.");
        }

        Set<PaymentMethod> accepted =
                PaymentMethod.normalizeAccepted(cart.getProvider().getAcceptedPaymentMethods());
        PaymentMethod chosen = req.paymentMethod();
        if (chosen == null || !chosen.isCheckoutSelectable() || !accepted.contains(chosen)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_METHOD",
                    "This provider does not accept "
                            + (chosen == null ? "that" : chosen.name())
                            + " payment. Accepted: "
                            + accepted.stream().map(PaymentMethod::name).sorted().toList());
        }
        if (chosen != PaymentMethod.PEACH && req.peachPaymentMethod() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PEACH_PAYMENT_METHOD",
                    "peachPaymentMethod is only valid when paymentMethod is PEACH.");
        }
        if (chosen == PaymentMethod.EFT) {
            String acc = cart.getProvider().getBankAccountNumber();
            if (acc == null || acc.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "BANK_DETAILS",
                        "This provider accepts EFT but has not published bank details yet. Choose Cash or contact the provider.");
            }
        }
        PeachPaymentMethod peachMethod = chosen == PaymentMethod.PEACH ? req.peachPaymentMethod() : null;
        if (chosen == PaymentMethod.PEACH && peachMethod == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PEACH_PAYMENT_METHOD",
                    "Choose Card or Instant EFT.");
        }
        if (chosen == PaymentMethod.PEACH && !peachPaymentService.isConfigured()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PEACH_NOT_CONFIGURED",
                    "Online payments are not available right now. Choose Cash or Manual EFT.");
        }

        List<CartLine> lines = new ArrayList<>(cart.getLines());
        List<Listing> lockedListings = new ArrayList<>();

        // Lock listings in consistent order (by ID) to prevent deadlocks
        List<Long> listingIds = lines.stream()
                .map(line -> line.getListing().getId())
                .sorted()
                .distinct()
                .toList();

        for (Long listingId : listingIds) {
            Listing listing = listingRepository
                    .findByIdWithLock(listingId)
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.BAD_REQUEST, "LISTING_MISSING", "Listing not found"));

            if (!listing.getProvider().getId().equals(providerId)) {
                throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_MIX", "Mixed-provider cart rejected");
            }
            if (!listing.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LISTING_INACTIVE",
                        "Listing '" + listing.getTitle() + "' is no longer available");
            }
            lockedListings.add(listing);
        }

        // Validate availability for each cart line
        for (CartLine line : lines) {
            Listing listing = lockedListings.stream()
                    .filter(l -> l.getId().equals(line.getListing().getId()))
                    .findFirst()
                    .orElseThrow();

            if (listing.getListingType() == ListingType.RENT) {
                var conflictingBookings = rentalBookingRepository.findOverlapping(
                        listing.getId(),
                        Set.of(BookingStatus.PENDING_PAYMENT),
                        line.getRentalStart(),
                        line.getRentalEnd());
                if (!conflictingBookings.isEmpty()) {
                    var firstConflict = conflictingBookings.get(0);
                    String conflictMessage = String.format(
                            "'%s' is already booked from %s to %s. Please select different dates.",
                            listing.getTitle(),
                            formatDate(firstConflict.getStartAt()),
                            formatDate(firstConflict.getEndAt()));
                    throw new ApiException(HttpStatus.CONFLICT, "RENTAL_CONFLICT", conflictMessage);
                }
            } else {
                // Validate stock for SALE listings (on-hand minus reservations)
                if (listing.getStockQuantity() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "STOCK_NOT_TRACKED",
                            "'" + listing.getTitle() + "' does not have stock tracking enabled");
                }
                int available = ListingStock.availableForSale(listing);
                if (available < line.getQuantity()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                            "Insufficient stock for '" + listing.getTitle() + "'. Available: " + available +
                                    ", Requested: " + line.getQuantity());
                }
                if (available == 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "OUT_OF_STOCK",
                            "'" + listing.getTitle() + "' is sold out");
                }
            }
        }

        BigDecimal saleTotal = BigDecimal.ZERO;
        List<CartLine> saleLines = new ArrayList<>();
        List<CartLine> rentLines = new ArrayList<>();
        for (CartLine line : lines) {
            Listing l = line.getListing();
            if (l.getListingType() == ListingType.SALE) {
                saleTotal = saleTotal.add(
                        l.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
                saleLines.add(line);
            } else {
                rentLines.add(line);
            }
        }

        List<Long> orderIds = new ArrayList<>();
        List<Long> bookingIds = new ArrayList<>();
        List<String> verificationCodes = new ArrayList<>();

        // Verification codes must be unique per record (DB constraint). We keep purchase + rentals linked via sessionKey.

        boolean distanceProvided = req.deliveryDistanceKm() != null
                && req.deliveryDistanceKm().compareTo(BigDecimal.ZERO) > 0;
        if (distanceProvided) {
            if (!cart.getProvider().isDeliveryAvailable()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DELIVERY_UNAVAILABLE",
                        "This provider does not offer delivery.");
            }
            if (cart.getProvider().getDeliveryPricePerKm() == null
                    || cart.getProvider().getDeliveryPricePerKm().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DELIVERY_MISCONFIGURED",
                        "Delivery pricing is not configured for this provider.");
            }
        }

        // Calculate delivery fee if applicable (server is source of truth)
        BigDecimal deliveryFee = BigDecimal.ZERO;
        if (distanceProvided) {
            deliveryFee = cart.getProvider()
                    .getDeliveryPricePerKm()
                    .multiply(req.deliveryDistanceKm())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        List<Order> createdOrders = new ArrayList<>();
        List<RentalBooking> createdRentals = new ArrayList<>();
        List<PaymentRecord> createdPayments = new ArrayList<>();

        if (!saleLines.isEmpty()) {
            Order order = new Order();
            order.setProvider(cart.getProvider());
            order.setGuestName(req.guestName());
            order.setGuestEmail(req.guestEmail());
            order.setGuestPhone(req.guestPhone());
            order.setDeliveryOrPickup(req.deliveryOrPickup());
            order.setDeliveryDistanceKm(req.deliveryDistanceKm());
            order.setDeliveryFee(deliveryFee);
            order.setTotalAmount(saleTotal.add(deliveryFee).setScale(2, RoundingMode.HALF_UP));
            order.setSessionKey(sessionKey);
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setVerificationCode(verificationCodeService.generateVerificationCode());

            for (CartLine line : saleLines) {
                Listing l = line.getListing();
                CartLine ol = new CartLine();
                ol.setOrder(order);
                ol.setListing(l);
                ol.setQuantity(line.getQuantity());
                order.getLines().add(ol);

                ListingStock.addReservation(l, line.getQuantity());
                listingRepository.save(l);
            }
            OrderRepository.save(order);
            orderIds.add(order.getId());
            verificationCodes.add(order.getVerificationCode());
            createdOrders.add(order);

            PaymentRecord pay = new PaymentRecord();
            pay.setProvider(cart.getProvider());
            pay.setOrder(order);
            pay.setMethod(req.paymentMethod());
            pay.setAmount(order.getTotalAmount());
            pay.setStatus(com.agrimarket.domain.PaymentRecordStatus.PENDING_PAYMENT);
            paymentRecordRepository.save(pay);
            createdPayments.add(pay);
        }

        for (CartLine line : rentLines) {
            Listing l = line.getListing();
            BigDecimal rentTotal = rentalPricingService.priceRental(l, line.getRentalStart(), line.getRentalEnd());
            RentalBooking b = new RentalBooking();
            b.setProvider(cart.getProvider());
            b.setListing(l);
            b.setGuestName(req.guestName());
            b.setGuestEmail(req.guestEmail());
            b.setGuestPhone(req.guestPhone());
            b.setDeliveryOrPickup(req.deliveryOrPickup());
            b.setDeliveryDistanceKm(req.deliveryDistanceKm());
            b.setDeliveryFee(deliveryFee);
            b.setStartAt(line.getRentalStart());
            b.setEndAt(line.getRentalEnd());
            b.setTotalAmount(rentTotal.add(deliveryFee).setScale(2, RoundingMode.HALF_UP));
            b.setSessionKey(sessionKey);
            b.setStatus(BookingStatus.PENDING_PAYMENT);
            b.setVerificationCode(verificationCodeService.generateVerificationCode());
            rentalBookingRepository.save(b);
            bookingIds.add(b.getId());
            verificationCodes.add(b.getVerificationCode());
            createdRentals.add(b);

            PaymentRecord pay = new PaymentRecord();
            pay.setProvider(cart.getProvider());
            pay.setRentalBooking(b);
            pay.setMethod(req.paymentMethod());
            pay.setAmount(b.getTotalAmount());
            pay.setStatus(com.agrimarket.domain.PaymentRecordStatus.PENDING_PAYMENT);
            paymentRecordRepository.save(pay);
            createdPayments.add(pay);
        }

        cart.getLines().clear();
        cart.setProvider(null);
        cart.setUpdatedAt(java.time.Instant.now());
        cartSessionRepository.save(cart);

        // Online checkout: create the Peach Hosted Checkout session covering all payment records
        // from this cart before returning — the whole checkout rolls back if this fails.
        String peachRedirectUrl = null;
        String peachCheckoutId = null;
        String peachMerchantRef = null;
        if (chosen == PaymentMethod.PEACH) {
            var checkout = peachPaymentService.initiateCartCheckout(createdPayments, peachMethod);
            peachRedirectUrl = checkout.redirectUrl();
            peachCheckoutId = checkout.checkoutId();
            peachMerchantRef = checkout.merchantTransactionId();
        }

        // Unified channels: in-app + email + SMS (Wheel Hub fan-out).
        try {
            appNotificationService.notifyCheckout(
                    createdOrders,
                    createdRentals,
                    req.guestName(),
                    req.guestEmail(),
                    req.guestPhone(),
                    req.paymentMethod() == null ? "" : req.paymentMethod().name(),
                    req.deliveryOrPickup());
        } catch (Exception ignored) {
            // Never fail checkout due to notifications.
        }

        // Backward-compatible response keys (older clients/tests used OrderIds/bookingIds)
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("purchaseOrderIds", orderIds);
        response.put("OrderIds", orderIds);
        response.put("rentalBookingIds", bookingIds);
        response.put("bookingIds", bookingIds);
        response.put("providerId", providerId);
        response.put("verificationCodes", verificationCodes);
        if (peachRedirectUrl != null) {
            response.put("redirectUrl", peachRedirectUrl);
            response.put("peachCheckoutId", peachCheckoutId);
            response.put("peachRef", peachMerchantRef);
        }
        return response;
    }

    /**
     * Formats an Instant date to a user-friendly string
     */
    private String formatDate(Instant instant) {
        if (instant == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }
}
