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
            long overlaps = rentalBookingRepository.countOverlapping(
                    listing.getId(),
                    Set.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED),
                    req.rentalStart(),
                    req.rentalEnd());
            if (overlaps > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "RENTAL_CONFLICT", "Selected dates are not available");
            }
        } else {
            if (listing.getStockQuantity() != null && listing.getStockQuantity() < req.quantity()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "Not enough stock");
            }
        }

        if (cart.getProvider() == null) {
            cart.setProvider(listing.getProvider());
        }

        CartLine line = new CartLine();
        line.setCartSession(cart);
        line.setListing(listing);
        line.setQuantity(req.quantity());
        line.setRentalStart(req.rentalStart());
        line.setRentalEnd(req.rentalEnd());
        cart.getLines().add(line);
        cart.setUpdatedAt(Instant.now());
        cartSessionRepository.save(cart);

        return buildResponse(cartSessionRepository.findBySessionKeyWithLines(sessionKey).orElseThrow());
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
                    line.getRentalEnd() != null ? line.getRentalEnd().toString() : null));
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
                rows,
                total.setScale(2, RoundingMode.HALF_UP));
    }
}
