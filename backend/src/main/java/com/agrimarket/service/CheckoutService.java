package com.agrimarket.service;

import com.agrimarket.api.dto.GuestCheckoutRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.CartSession;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import com.agrimarket.domain.OrderLine;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.PurchaseOrder;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.CartSessionRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.PurchaseOrderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final CartSessionRepository cartSessionRepository;
    private final ListingRepository listingRepository;

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
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "LISTING_MISSING", "Listing not found"));

            if (!listing.getProvider().getId().equals(providerId)) {
                throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_MIX", "Mixed-provider cart rejected");
            }
            if (!listing.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LISTING_INACTIVE", "Listing '" + listing.getTitle() + "' is no longer available");
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
                long overlaps = rentalBookingRepository.countOverlapping(
                        listing.getId(),
                        Set.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED),
                        line.getRentalStart(),
                        line.getRentalEnd());
                if (overlaps > 0) {
                    throw new ApiException(HttpStatus.CONFLICT, "RENTAL_CONFLICT",
                            "'" + listing.getTitle() + "' is no longer available for the selected dates");
                }
            } else {
                // Validate stock for SALE listings
                if (listing.getStockQuantity() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "STOCK_NOT_TRACKED",
                            "'" + listing.getTitle() + "' does not have stock tracking enabled");
                }
                if (listing.getStockQuantity() < line.getQuantity()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                            "Insufficient stock for '" + listing.getTitle() + "'. Available: " + listing.getStockQuantity() +
                            ", Requested: " + line.getQuantity());
                }
                if (listing.getStockQuantity() == 0) {
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

        if (!saleLines.isEmpty()) {
            PurchaseOrder order = new PurchaseOrder();
            order.setProvider(cart.getProvider());
            order.setGuestName(req.guestName());
            order.setGuestEmail(req.guestEmail());
            order.setGuestPhone(req.guestPhone());
            order.setDeliveryOrPickup(req.deliveryOrPickup());
            order.setTotalAmount(saleTotal.setScale(2, RoundingMode.HALF_UP));
            order.setSessionKey(sessionKey);
            order.setStatus(OrderStatus.PAID);

            for (CartLine line : saleLines) {
                Listing l = line.getListing();
                OrderLine ol = new OrderLine();
                ol.setOrder(order);
                ol.setListing(l);
                ol.setQuantity(line.getQuantity());
                ol.setUnitPrice(l.getUnitPrice());
                order.getLines().add(ol);

                if (l.getStockQuantity() != null) {
                    l.setStockQuantity(l.getStockQuantity() - line.getQuantity());
                    listingRepository.save(l);
                }
            }
            purchaseOrderRepository.save(order);
            orderIds.add(order.getId());

            PaymentRecord pay = new PaymentRecord();
            pay.setProvider(cart.getProvider());
            pay.setPurchaseOrder(order);
            pay.setMethod(req.paymentMethod());
            pay.setAmount(order.getTotalAmount());
            pay.setStatus(PaymentStatus.COMPLETED);
            paymentRecordRepository.save(pay);
        }

        for (CartLine line : rentLines) {
            Listing l = line.getListing();
            BigDecimal rentTotal =
                    rentalPricingService.priceRental(l, line.getRentalStart(), line.getRentalEnd());
            RentalBooking b = new RentalBooking();
            b.setProvider(cart.getProvider());
            b.setListing(l);
            b.setGuestName(req.guestName());
            b.setGuestEmail(req.guestEmail());
            b.setGuestPhone(req.guestPhone());
            b.setDeliveryOrPickup(req.deliveryOrPickup());
            b.setStartAt(line.getRentalStart());
            b.setEndAt(line.getRentalEnd());
            b.setTotalAmount(rentTotal.setScale(2, RoundingMode.HALF_UP));
            b.setSessionKey(sessionKey);
            b.setStatus(BookingStatus.CONFIRMED);
            rentalBookingRepository.save(b);
            bookingIds.add(b.getId());

            PaymentRecord pay = new PaymentRecord();
            pay.setProvider(cart.getProvider());
            pay.setRentalBooking(b);
            pay.setMethod(req.paymentMethod());
            pay.setAmount(b.getTotalAmount());
            pay.setStatus(PaymentStatus.COMPLETED);
            paymentRecordRepository.save(pay);
        }

        cart.getLines().clear();
        cart.setProvider(null);
        cart.setUpdatedAt(java.time.Instant.now());
        cartSessionRepository.save(cart);

        return Map.of(
                "purchaseOrderIds", orderIds,
                "bookingIds", bookingIds,
                "providerId", providerId);
    }
}
