package com.agrimarket.service;

import com.agrimarket.api.dto.GuestCheckoutRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.util.InputValidator;
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
import com.agrimarket.domain.Provider;
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
    private final RentalAvailability rentalAvailability;
    private final DeliveryPricing deliveryPricing;

    @Transactional
    public Map<String, Object> guestCheckout(String sessionKey, GuestCheckoutRequest req) {
        // Formats are checked on the request DTO; store a tidy, consistent form (phone as +27...).
        String guestName = req.guestName() == null ? null : req.guestName().trim().replaceAll("\\s+", " ");
        String guestEmail = req.guestEmail() == null ? null : req.guestEmail().trim();
        String guestPhone = InputValidator.normalizePhoneOrKeep(req.guestPhone());
        CartSession cart = cartService.requireCartWithLines(sessionKey);

        // A cart may hold several sellers; each is checked out on its own (payment methods, delivery and
        // bank details are per seller). The other sellers' items stay in the cart.
        Map<Long, Provider> sellersInCart = new LinkedHashMap<>();
        for (CartLine line : cart.getLines()) {
            Provider p = line.getListing().getProvider();
            sellersInCart.putIfAbsent(p.getId(), p);
        }
        Long providerId = req.providerId();
        if (providerId == null) {
            if (sellersInCart.size() > 1) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "CHOOSE_SELLER",
                        "Your cart has items from more than one seller. Choose which seller to check out first.");
            }
            providerId = sellersInCart.keySet().iterator().next();
        }
        Provider seller = sellersInCart.get(providerId);
        if (seller == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SELLER_NOT_IN_CART",
                    "There are no items from that seller in your cart.");
        }
        final Long sellerId = providerId;

        if (seller.getStatus() != ProviderStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_INACTIVE", "Provider is not active");
        }
        if (!subscriptionService.hasActiveSubscription(providerId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SUBSCRIPTION_INACTIVE",
                    "This provider cannot accept orders right now.");
        }

        Set<PaymentMethod> accepted =
                PaymentMethod.normalizeAccepted(seller.getAcceptedPaymentMethods());
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
            String acc = seller.getBankAccountNumber();
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

        // Only this seller's lines; the rest of the cart is left untouched.
        List<CartLine> lines = cart.getLines().stream()
                .filter(line -> line.getListing().getProvider().getId().equals(sellerId))
                .toList();
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
                // Final check, with the listing row locked: no booking that is awaiting payment, paid or
                // out on hire may overlap (previously only unpaid ones were checked here), and this
                // checkout must not book the same item twice for overlapping times.
                rentalAvailability.validateWindow(line.getRentalStart(), line.getRentalEnd());
                rentalAvailability.requireFree(listing, line.getRentalStart(), line.getRentalEnd());
                rentalAvailability.requireNotInLines(
                        listing, line.getRentalStart(), line.getRentalEnd(), lines, line);
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

        // Delivery is priced on the server from the seller's pickup pin and the buyer's map pin.
        // Any distance the client sends is ignored, so the fee can't be lowered by typing a shorter one.
        boolean delivery = "DELIVERY".equalsIgnoreCase(req.deliveryOrPickup() == null ? "" : req.deliveryOrPickup().trim());
        BigDecimal deliveryFee = BigDecimal.ZERO;
        BigDecimal deliveryKm = null;
        Double buyerLat = parseCoordinate(req.latitude());
        Double buyerLng = parseCoordinate(req.longitude());
        String deliveryAddress = null;
        if (delivery) {
            DeliveryPricing.Quote quote = deliveryPricing.quote(seller, buyerLat, buyerLng);
            deliveryFee = quote.fee();
            deliveryKm = quote.distanceKm();
            deliveryAddress = req.deliveryAddress() == null || req.deliveryAddress().isBlank()
                    ? null
                    : req.deliveryAddress().trim();
        } else {
            buyerLat = null;
            buyerLng = null;
        }

        // One reference for everything this checkout creates (this seller, this payment).
        String checkoutRef = java.util.UUID.randomUUID().toString();
        List<Order> createdOrders = new ArrayList<>();
        List<RentalBooking> createdRentals = new ArrayList<>();
        List<PaymentRecord> createdPayments = new ArrayList<>();
        // One seller, one trip: the delivery fee is charged once per checkout (on the purchase order if
        // there is one, otherwise on the first rental), not on every order and rental.
        BigDecimal deliveryFeeLeft = deliveryFee;

        if (!saleLines.isEmpty()) {
            Order order = new Order();
            order.setProvider(seller);
            order.setGuestName(guestName);
            order.setGuestEmail(guestEmail);
            order.setGuestPhone(guestPhone);
            order.setDeliveryOrPickup(req.deliveryOrPickup());
            order.setDeliveryDistanceKm(deliveryKm);
            order.setDeliveryAddress(deliveryAddress);
            order.setDeliveryLatitude(buyerLat);
            order.setDeliveryLongitude(buyerLng);
            order.setDeliveryFee(deliveryFeeLeft);
            order.setTotalAmount(saleTotal.add(deliveryFeeLeft).setScale(2, RoundingMode.HALF_UP));
            deliveryFeeLeft = BigDecimal.ZERO;
            order.setSessionKey(sessionKey);
            order.setCheckoutRef(checkoutRef);
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
            pay.setProvider(seller);
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
            b.setProvider(seller);
            b.setListing(l);
            b.setGuestName(guestName);
            b.setGuestEmail(guestEmail);
            b.setGuestPhone(guestPhone);
            b.setDeliveryOrPickup(req.deliveryOrPickup());
            b.setDeliveryDistanceKm(deliveryKm);
            b.setDeliveryAddress(deliveryAddress);
            b.setLatitude(buyerLat == null ? null : String.valueOf(buyerLat));
            b.setLongitude(buyerLng == null ? null : String.valueOf(buyerLng));
            b.setDeliveryFee(deliveryFeeLeft);
            b.setStartAt(line.getRentalStart());
            b.setEndAt(line.getRentalEnd());
            b.setTotalAmount(rentTotal.add(deliveryFeeLeft).setScale(2, RoundingMode.HALF_UP));
            deliveryFeeLeft = BigDecimal.ZERO;
            b.setSessionKey(sessionKey);
            b.setCheckoutRef(checkoutRef);
            b.setStatus(BookingStatus.PENDING_PAYMENT);
            b.setVerificationCode(verificationCodeService.generateVerificationCode());
            rentalBookingRepository.save(b);
            bookingIds.add(b.getId());
            verificationCodes.add(b.getVerificationCode());
            createdRentals.add(b);

            PaymentRecord pay = new PaymentRecord();
            pay.setProvider(seller);
            pay.setRentalBooking(b);
            pay.setMethod(req.paymentMethod());
            pay.setAmount(b.getTotalAmount());
            pay.setStatus(com.agrimarket.domain.PaymentRecordStatus.PENDING_PAYMENT);
            paymentRecordRepository.save(pay);
            createdPayments.add(pay);
        }

        // Remove only this seller's items; other sellers stay in the cart for their own checkout.
        cart.getLines().removeIf(line -> line.getListing().getProvider().getId().equals(sellerId));
        cart.setProvider(null);
        cart.setUpdatedAt(java.time.Instant.now());
        cartSessionRepository.save(cart);
        int sellersLeft = (int) cart.getLines().stream()
                .map(line -> line.getListing().getProvider().getId())
                .distinct()
                .count();

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
                    guestName,
                    guestEmail,
                    guestPhone,
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
        response.put("providerName", seller.getName());
        response.put("verificationCodes", verificationCodes);
        // Other sellers still waiting in the cart (the page moves on to the next one).
        response.put("sellersRemaining", sellersLeft);
        if (peachRedirectUrl != null) {
            response.put("redirectUrl", peachRedirectUrl);
            response.put("peachCheckoutId", peachCheckoutId);
            response.put("peachRef", peachMerchantRef);
        }
        return response;
    }

    /** Parses a map coordinate sent by the browser (validated by the request pattern); null when blank. */
    static Double parseCoordinate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
