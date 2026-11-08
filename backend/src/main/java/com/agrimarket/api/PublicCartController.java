package com.agrimarket.api;

import com.agrimarket.api.dto.CartAddRequest;
import com.agrimarket.api.dto.CartResponse;
import com.agrimarket.api.dto.GuestCheckoutRequest;
import com.agrimarket.service.CartService;
import com.agrimarket.service.CheckoutService;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/cart")
@RequiredArgsConstructor
public class PublicCartController {

    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final com.agrimarket.repo.ProviderRepository providerRepository;
    private final com.agrimarket.service.DeliveryPricing deliveryPricing;

    private static final String SESSION_HEADER = "X-Session-Id";

    @PostMapping("/session")
    public Map<String, String> createSession() {
        // simple session id: caller can also provide their own, but frontend expects this.
        String sessionId = "sess-" + java.util.UUID.randomUUID();
        return Map.of("sessionId", sessionId);
    }

    /**
     * Delivery distance and fee for a seller to the buyer's map pin, shown at checkout before ordering.
     * Uses the same server-side pricing as checkout. Never returns the seller's coordinates.
     */
    @GetMapping("/delivery-quote")
    public Map<String, Object> deliveryQuote(
            @RequestParam("providerId") Long providerId,
            @RequestParam("lat") Double lat,
            @RequestParam("lng") Double lng) {
        var seller = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new com.agrimarket.api.error.ApiException(
                        HttpStatus.NOT_FOUND, "PROVIDER", "Seller not found"));
        var q = deliveryPricing.quote(seller, lat, lng);
        return Map.of("distanceKm", q.distanceKm(), "fee", q.fee(), "ratePerKm", q.ratePerKm());
    }

    @GetMapping
    public CartResponse get(@RequestHeader(SESSION_HEADER) String sessionId) {
        return cartService.getCart(sessionId);
    }

    @PostMapping("/add")
    public CartResponse add(@RequestHeader(SESSION_HEADER) String sessionId, @Valid @RequestBody CartAddRequest req) {
        return cartService.addItem(sessionId, req);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@RequestHeader(SESSION_HEADER) String sessionId) {
        cartService.clear(sessionId);
    }

    @PatchMapping("/items/{cartLineId}/quantity")
    public CartResponse updateQuantity(
            @RequestHeader(SESSION_HEADER) String sessionId,
            @PathVariable Long cartLineId,
            @Valid @RequestBody com.agrimarket.api.dto.UpdateQuantityRequest req) {
        return cartService.updateQuantity(sessionId, cartLineId, req.quantity());
    }

    @DeleteMapping("/items/{cartLineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader(SESSION_HEADER) String sessionId, @PathVariable Long cartLineId) {
        cartService.removeItem(sessionId, cartLineId);
    }

    @PostMapping("/checkout")
    public Map<String, Object> checkout(@RequestHeader(SESSION_HEADER) String sessionId, @Valid @RequestBody GuestCheckoutRequest req) {
        return checkoutService.guestCheckout(sessionId, req);
    }
}

