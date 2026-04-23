package com.agrimarket.api;

import com.agrimarket.api.dto.CartAddRequest;
import com.agrimarket.api.dto.CartResponse;
import com.agrimarket.api.dto.GuestCheckoutRequest;
import com.agrimarket.api.dto.UpdateQuantityRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.service.CartService;
import com.agrimarket.service.CheckoutService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/cart")
@RequiredArgsConstructor
public class PublicCartController {

    public static final String SESSION_HEADER = "X-Session-Id";

    private final CartService cartService;
    private final CheckoutService checkoutService;

    @PostMapping("/session")
    public Map<String, String> newSession() {
        return Map.of("sessionId", UUID.randomUUID().toString());
    }

    @PostMapping("/add")
    public CartResponse add(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @Valid @RequestBody CartAddRequest body) {
        return cartService.addItem(requireSession(sessionId), body);
    }

    @GetMapping
    public CartResponse get(@RequestHeader(value = SESSION_HEADER, required = false) String sessionId) {
        return cartService.getCart(requireSession(sessionId));
    }

    @DeleteMapping
    public void clear(@RequestHeader(value = SESSION_HEADER, required = false) String sessionId) {
        cartService.clear(requireSession(sessionId));
    }

    @DeleteMapping("/items/{cartLineId}")
    public CartResponse removeItem(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @PathVariable Long cartLineId) {
        return cartService.removeItem(requireSession(sessionId), cartLineId);
    }

    @PatchMapping("/items/{cartLineId}/quantity")
    public CartResponse updateQuantity(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @PathVariable Long cartLineId,
            @Valid @RequestBody UpdateQuantityRequest payload) {
        return cartService.updateQuantity(requireSession(sessionId), cartLineId, payload.quantity());
    }

    @PostMapping("/checkout")
    public Map<String, Object> checkout(
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @Valid @RequestBody GuestCheckoutRequest body) {
        return checkoutService.guestCheckout(requireSession(sessionId), body);
    }

    private static String requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SESSION", "Missing X-Session-Id header");
        }
        return sessionId;
    }
}
