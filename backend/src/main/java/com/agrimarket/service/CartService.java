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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.agrimarket.domain.Provider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
public class CartService {

    private final CartSessionRepository cartSessionRepository;
    private final ListingRepository listingRepository;
    private final RentalBookingRepository rentalBookingRepository;
    private final RentalPricingService rentalPricingService;
    private final AppProperties appProperties;
    private final OnlinePaymentGatewayResolver gatewayResolver;
    private final SubscriptionService subscriptionService;
    private final RentalAvailability rentalAvailability;

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
        // Same rule as the public feed and checkout: hidden listings can't be added from a stale page.
        if (!subscriptionService.canAcceptOrders(listing.getProvider())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROVIDER_UNAVAILABLE",
                    "This item is no longer available. Please choose another listing.");
        }

        // A cart can hold items from several sellers; each seller is checked out separately.
        if (listing.getListingType() == ListingType.RENT) {
            // One item, one slot: not in the past, not already booked by anyone, not already in this cart.
            rentalAvailability.validateWindow(req.rentalStart(), req.rentalEnd());
            rentalAvailability.requireFree(listing, req.rentalStart(), req.rentalEnd());
            rentalAvailability.requireNotInLines(listing, req.rentalStart(), req.rentalEnd(), cart.getLines(), null);
        } else {
            int avail = ListingStock.availableForSale(listing);
            if (avail != Integer.MAX_VALUE && avail < req.quantity()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "Not enough stock");
            }
        }

        // Check if item already exists in cart
        CartLine existingLine = findExistingCartLine(cart, listing, req.rentalStart(), req.rentalEnd());

        if (existingLine != null) {
            // Item already in cart - increment quantity
            int newQuantity = existingLine.getQuantity() + req.quantity();

            // Validate new quantity for SALE items
            if (listing.getListingType() == ListingType.SALE) {
                int avail = ListingStock.availableForSale(listing);
                if (avail != Integer.MAX_VALUE && avail < newQuantity) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                            "Cannot add " + req.quantity() + " more. You already have " + existingLine.getQuantity() +
                            " in cart. Available stock: " + avail);
                }
            }

            existingLine.setQuantity(newQuantity);
        } else {
            // New item - add to cart
            CartLine line = new CartLine();
            line.setCartSession(cart);
            line.setListing(listing);
            // A rental books one item for a time slot; quantity only applies to items for sale.
            line.setQuantity(listing.getListingType() == ListingType.RENT ? 1 : req.quantity());
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
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        List.of()));
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

        if (listing.getListingType() == ListingType.RENT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "RENTAL_QUANTITY",
                    "A rental books one item for the chosen time. To book it for another time, add it again with those dates.");
        }

        // Validate stock for SALE items
        if (listing.getListingType() == ListingType.SALE) {
            int avail = ListingStock.availableForSale(listing);
            if (avail != Integer.MAX_VALUE && avail < newQuantity) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK",
                        "Cannot update quantity. Available: " + avail + ", Requested: " + newQuantity);
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
        // Seller groups in the order their first item was added (stable as the cart changes).
        Map<Long, Provider> sellers = new LinkedHashMap<>();
        Map<Long, List<Long>> lineIdsBySeller = new LinkedHashMap<>();
        Map<Long, Integer> itemsBySeller = new LinkedHashMap<>();
        Map<Long, BigDecimal> subtotalBySeller = new LinkedHashMap<>();

        List<CartLine> ordered = new ArrayList<>(cart.getLines());
        ordered.sort(Comparator.comparing(CartLine::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (CartLine line : ordered) {
            Listing l = line.getListing();
            Provider p = l.getProvider();
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
                    (l.getListingType() == ListingType.SALE)
                            ? (l.getStockQuantity() != null ? ListingStock.availableForSale(l) : null)
                            : l.getStockQuantity(),
                    p.getId(),
                    p.getName()));
            sellers.putIfAbsent(p.getId(), p);
            lineIdsBySeller.computeIfAbsent(p.getId(), k -> new ArrayList<>()).add(line.getId());
            itemsBySeller.merge(p.getId(), l.getListingType() == ListingType.SALE ? line.getQuantity() : 1, Integer::sum);
            subtotalBySeller.merge(p.getId(), lineTotal, BigDecimal::add);
        }

        List<CartResponse.SellerGroup> groups = new ArrayList<>();
        for (Provider p : sellers.values()) {
            groups.add(new CartResponse.SellerGroup(
                    p.getId(),
                    p.getName(),
                    p.getLocation(),
                    p.getBankName(),
                    p.getBankAccountName(),
                    p.getBankAccountNumber(),
                    p.getBankBranchCode(),
                    p.getBankReference(),
                    acceptedAtCheckout(p),
                    p.isDeliveryAvailable(),
                    p.getDeliveryPricePerKm(),
                    lineIdsBySeller.get(p.getId()),
                    itemsBySeller.get(p.getId()),
                    subtotalBySeller.get(p.getId()).setScale(2, RoundingMode.HALF_UP)));
        }

        // Older clients: describe the seller only when there is exactly one.
        CartResponse.SellerGroup only = groups.size() == 1 ? groups.get(0) : null;
        return new CartResponse(
                cart.getSessionKey(),
                only != null ? only.providerId() : null,
                only != null ? only.providerName() : null,
                only != null ? only.providerLocation() : null,
                only != null ? only.bankName() : null,
                only != null ? only.bankAccountName() : null,
                only != null ? only.bankAccountNumber() : null,
                only != null ? only.bankBranchCode() : null,
                only != null ? only.bankReference() : null,
                only != null ? only.acceptedPaymentMethods() : PaymentMethod.defaultAccepted(),
                only != null ? only.deliveryAvailable() : null,
                only != null ? only.deliveryPricePerKm() : null,
                rows,
                total.setScale(2, RoundingMode.HALF_UP),
                groups);
    }

    /** The seller's accepted methods, minus the online option when no online gateway is configured. */
    private Set<PaymentMethod> acceptedAtCheckout(Provider p) {
        Set<PaymentMethod> accepted = PaymentMethod.normalizeAccepted(p.getAcceptedPaymentMethods());
        if (!gatewayResolver.isOnlineAvailable() && accepted.contains(PaymentMethod.PEACH)) {
            EnumSet<PaymentMethod> withoutPeach = EnumSet.copyOf(accepted);
            withoutPeach.remove(PaymentMethod.PEACH);
            return withoutPeach;
        }
        return accepted;
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
