package com.agrimarket.service;

import com.agrimarket.api.dto.CartAddRequest;
import com.agrimarket.api.dto.CartResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.AppProperties;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.CartSession;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.repo.CartSessionRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.RentalBookingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartSessionRepository cartSessionRepository;
    private final ListingRepository listingRepository;
    private final RentalBookingRepository rentalBookingRepository;
    private final RentalPricingService rentalPricingService;
    private final AppProperties appProperties;

    @Transactional
    public CartResponse addItem(String sessionKey, CartAddRequest req) {
        CartSession cart = getOrCreate(sessionKey);
        expireIfNeeded(cart);

        Listing listing = listingRepository
                .findById(req.listingId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "Listing not found"));

        if (!listing.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LISTING_INACTIVE", "Listing is not available");
        }
        if (listing.getProvider().getStatus() != ProviderStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_UNAVAILABLE", "Provider is not accepting orders");
        }

        Long listingProviderId = listing.getProvider().getId();
        if (cart.getProvider() != null && !cart.getProvider().getId().equals(listingProviderId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROVIDER_LOCK",
                    "Cart is locked to another provider. Complete or clear the cart first.");
        }

        if (listing.getListingType() == ListingType.RENT) {
            if (req.rentalStart() == null || req.rentalEnd() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RENTAL_DATES_REQUIRED", "Rental start and end required");
            }
            var conflictingBookings = rentalBookingRepository.findOverlapping(
                    listing.getId(),
                    Set.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED),
                    req.rentalStart(),
                    req.rentalEnd());
            if (!conflictingBookings.isEmpty()) {
                var firstConflict = conflictingBookings.get(0);
                String conflictMessage = String.format(
                        "This item is already booked from %s to %s. Please select different dates.",
                        formatDate(firstConflict.getStartAt()),
                        formatDate(firstConflict.getEndAt()));
                throw new ApiException(HttpStatus.CONFLICT, "RENTAL_CONFLICT", conflictMessage);
            }
        } else {
            if (listing.getStockQuantity() != null && listing.getStockQuantity() < req.quantity()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "Not enough stock");
            }
        }

        if (cart.getProvider() == null) {
            cart.setProvider(listing.getProvider());
        }

        // Check if item already exists in cart
        CartLine existingLine = findExistingCartLine(cart, listing, req.rentalStart(), req.rentalEnd());

        if (existingLine != null) {
            // Item already in cart - increment quantity
            int newQuantity = existingLine.getQuantity() + req.quantity();

            // Validate new quantity for SALE items
            if (listing.getListingType() == ListingType.SALE) {
                if (listing.getStockQuantity() != null && listing.getStockQuantity() < newQuantity) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                            "Cannot add " + req.quantity() + " more. You already have " + existingLine.getQuantity() +
                            " in cart. Available stock: " + listing.getStockQuantity());
                }
            }

            existingLine.setQuantity(newQuantity);
        } else {
            // New item - add to cart
            CartLine line = new CartLine();
            line.setCartSession(cart);
            line.setListing(listing);
            line.setQuantity(req.quantity());
            line.setRentalStart(req.rentalStart());
            line.setRentalEnd(req.rentalEnd());
            cart.getLines().add(line);
        }

        cart.setUpdatedAt(Instant.now());
        cartSessionRepository.save(cart);

        return buildResponse(cartSessionRepository.findBySessionKeyWithLines(sessionKey).orElseThrow());
    }

    /**
     * Finds an existing cart line for the same listing and rental dates (if applicable)
     */
    private CartLine findExistingCartLine(CartSession cart, Listing listing, Instant rentalStart, Instant rentalEnd) {
        for (CartLine line : cart.getLines()) {
            if (!line.getListing().getId().equals(listing.getId())) {
                continue;
            }

            // For SALE items, just match by listing ID
            if (listing.getListingType() == ListingType.SALE) {
                return line;
            }

            // For RENT items, also match rental dates
            if (listing.getListingType() == ListingType.RENT) {
                boolean datesMatch = (line.getRentalStart() != null && line.getRentalStart().equals(rentalStart)) &&
                                    (line.getRentalEnd() != null && line.getRentalEnd().equals(rentalEnd));
                if (datesMatch) {
                    return line;
                }
            }
        }
        return null;
    }

    @Transactional
    public CartResponse getCart(String sessionKey) {
        return cartSessionRepository
                .findBySessionKeyWithLines(sessionKey)
                .map(cart -> {
                    expireIfNeeded(cart);
                    cartSessionRepository.save(cart);
                    return buildResponse(cart);
                })
                .orElseGet(() -> new CartResponse(
                        sessionKey,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
    }

    @Transactional
    public void clear(String sessionKey) {
        cartSessionRepository
                .findBySessionKeyWithLines(sessionKey)
                .ifPresent(cart -> {
                    cart.getLines().clear();
                    cart.setProvider(null);
                    cart.setUpdatedAt(Instant.now());
                    cartSessionRepository.save(cart);
                });
    }

    @Transactional
    public CartResponse removeItem(String sessionKey, Long cartLineId) {
        CartSession cart = cartSessionRepository
                .findBySessionKeyWithLines(sessionKey)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CART_EMPTY", "Cart is empty"));

        CartLine lineToRemove = cart.getLines().stream()
                .filter(line -> line.getId().equals(cartLineId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Item not found in cart"));

        cart.getLines().remove(lineToRemove);

        // If cart is now empty, clear provider lock
        if (cart.getLines().isEmpty()) {
            cart.setProvider(null);
        }

        cart.setUpdatedAt(Instant.now());
        cartSessionRepository.save(cart);

        return buildResponse(cartSessionRepository.findBySessionKeyWithLines(sessionKey).orElseThrow());
    }

    @Transactional
    public CartResponse updateQuantity(String sessionKey, Long cartLineId, int newQuantity) {
        if (newQuantity < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Quantity must be at least 1");
        }

        CartSession cart = cartSessionRepository
                .findBySessionKeyWithLines(sessionKey)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CART_EMPTY", "Cart is empty"));

        CartLine line = cart.getLines().stream()
                .filter(l -> l.getId().equals(cartLineId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Item not found in cart"));

        Listing listing = line.getListing();

        // Validate stock for SALE items
        if (listing.getListingType() == ListingType.SALE) {
            if (listing.getStockQuantity() != null && listing.getStockQuantity() < newQuantity) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                        "Cannot update quantity. Available: " + listing.getStockQuantity() + ", Requested: " + newQuantity);
            }
        }

        line.setQuantity(newQuantity);
        cart.setUpdatedAt(Instant.now());
        cartSessionRepository.save(cart);

        return buildResponse(cartSessionRepository.findBySessionKeyWithLines(sessionKey).orElseThrow());
    }

    @Transactional(readOnly = true)
    public CartSession requireCartWithLines(String sessionKey) {
        CartSession cart = cartSessionRepository
                .findBySessionKeyWithLines(sessionKey)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CART_EMPTY", "Cart is empty"));
        if (cart.getLines().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CART_EMPTY", "Cart is empty");
        }
        return cart;
    }

    private CartSession getOrCreate(String sessionKey) {
        return cartSessionRepository.findBySessionKeyWithLines(sessionKey).orElseGet(() -> newCart(sessionKey));
    }

    private CartSession newCart(String sessionKey) {
        CartSession c = new CartSession();
        c.setSessionKey(sessionKey);
        c.setUpdatedAt(Instant.now());
        return cartSessionRepository.save(c);
    }

    private void expireIfNeeded(CartSession cart) {
        if (cart.getId() == null) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(appProperties.cart().sessionTtlHours() * 3600L);
        if (cart.getUpdatedAt().isBefore(cutoff)) {
            cart.getLines().clear();
            cart.setProvider(null);
            cart.setUpdatedAt(Instant.now());
        }
    }

    private CartResponse buildResponse(CartSession cart) {
        BigDecimal total = BigDecimal.ZERO;
        List<CartResponse.CartLineResponse> rows = new ArrayList<>();
        for (CartLine line : cart.getLines()) {
            Listing l = line.getListing();
            BigDecimal lineTotal;
            if (l.getListingType() == ListingType.SALE) {
                lineTotal = l.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            } else {
                lineTotal = rentalPricingService.priceRental(l, line.getRentalStart(), line.getRentalEnd());
            }
            total = total.add(lineTotal);
            rows.add(new CartResponse.CartLineResponse(
                    line.getId(),
                    l.getId(),
                    l.getTitle(),
                    l.getListingType().name(),
                    line.getQuantity(),
                    lineTotal.setScale(2, RoundingMode.HALF_UP),
                    line.getRentalStart() != null ? line.getRentalStart().toString() : null,
                    line.getRentalEnd() != null ? line.getRentalEnd().toString() : null,
                    l.getStockQuantity()));
        }
        Long pid = cart.getProvider() != null ? cart.getProvider().getId() : null;
        String pname = cart.getProvider() != null ? cart.getProvider().getName() : null;
        String plocation = cart.getProvider() != null ? cart.getProvider().getLocation() : null;
        String bankName = cart.getProvider() != null ? cart.getProvider().getBankName() : null;
        String bankAccName = cart.getProvider() != null ? cart.getProvider().getBankAccountName() : null;
        String bankAccNo = cart.getProvider() != null ? cart.getProvider().getBankAccountNumber() : null;
        String bankBranch = cart.getProvider() != null ? cart.getProvider().getBankBranchCode() : null;
        String bankRef = cart.getProvider() != null ? cart.getProvider().getBankReference() : null;
        Set<PaymentMethod> accepted = EnumSet.allOf(PaymentMethod.class);
        if (cart.getProvider() != null
                && cart.getProvider().getAcceptedPaymentMethods() != null
                && !cart.getProvider().getAcceptedPaymentMethods().isEmpty()) {
            accepted = EnumSet.copyOf(cart.getProvider().getAcceptedPaymentMethods());
        }
        Boolean deliveryAvailable = cart.getProvider() != null ? cart.getProvider().isDeliveryAvailable() : null;
        BigDecimal deliveryPricePerKm = cart.getProvider() != null ? cart.getProvider().getDeliveryPricePerKm() : null;
        return new CartResponse(
                cart.getSessionKey(),
                pid,
                pname,
                plocation,
                bankName,
                bankAccName,
                bankAccNo,
                bankBranch,
                bankRef,
                accepted,
                deliveryAvailable,
                deliveryPricePerKm,
                rows,
                total.setScale(2, RoundingMode.HALF_UP));
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
