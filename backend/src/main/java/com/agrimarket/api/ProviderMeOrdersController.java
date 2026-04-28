package com.agrimarket.api;

import com.agrimarket.api.dto.BookingVerificationResponse;
import com.agrimarket.api.dto.OrderResponse;
import com.agrimarket.api.dto.OrderVerificationResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.OrderManagementService;
import com.agrimarket.service.TenantAccess;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me")
@RequiredArgsConstructor
public class ProviderMeOrdersController {

    private final OrderManagementService orderManagementService;
    private final OrderRepository orderRepository;
    private final RentalBookingRepository rentalBookingRepository;
    private final ListingRepository listingRepository;

    public record OrderItemResponse(Long listingId, String listingName, int quantity) {}

    public record StockUpdateItem(Long listingId, Integer quantity) {}

    @GetMapping("/orders/purchases")
    @Transactional(readOnly = true)
    public Page<OrderResponse> listPurchases(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size) {
        TenantAccess.requireProviderUser(actor);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.findByProvider_IdOrderByCreatedAtDesc(actor.getProviderId(), pageable).map(OrderResponse::from);
    }

    @GetMapping("/orders/rentals")
    @Transactional(readOnly = true)
    public Page<RentalBooking> listRentals(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderManagementService.getProviderRentals(actor.getProviderId(), pageable);
    }

    @GetMapping("/orders/purchases/{id}")
    @Transactional(readOnly = true)
    public OrderResponse getPurchase(@AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable Long id) {
        TenantAccess.requireProviderUser(actor);
        Order o = orderRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER", "Order not found"));
        if (!o.getProvider().getId().equals(actor.getProviderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROVIDER", "Not your order");
        }
        return OrderResponse.from(o);
    }

    @GetMapping("/orders/rentals/{id}")
    @Transactional(readOnly = true)
    public RentalBooking getRental(@AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable Long id) {
        TenantAccess.requireProviderUser(actor);
        RentalBooking b = rentalBookingRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING", "Booking not found"));
        if (!b.getProvider().getId().equals(actor.getProviderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROVIDER", "Not your booking");
        }
        return b;
    }

    @GetMapping("/orders/purchases/{orderId}/items")
    @Transactional(readOnly = true)
    public List<OrderItemResponse> getPurchaseItems(
            @AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable Long orderId) {
        TenantAccess.requireProviderUser(actor);
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER", "Order not found"));
        if (!o.getProvider().getId().equals(actor.getProviderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROVIDER", "Not your order");
        }
        return o.getLines().stream()
                .map(l -> new OrderItemResponse(
                        l.getListing().getId(),
                        l.getListing().getTitle(),
                        l.getQuantity()))
                .toList();
    }

    @PutMapping("/orders/purchases/{id}/status")
    public void updatePurchaseStatus(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long id,
            @RequestParam("status") OrderStatus status) {
        orderManagementService.updateOrderStatus(actor.getProviderId(), id, status);
    }

    @PutMapping("/orders/rentals/{id}/status")
    public void updateRentalStatus(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long id,
            @RequestParam("status") BookingStatus status) {
        orderManagementService.updateRentalStatus(actor.getProviderId(), id, status);
    }

    @DeleteMapping("/orders/purchases/{id}")
    public void deletePurchase(@AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable Long id) {
        orderManagementService.deleteOrder(actor, id);
    }

    @DeleteMapping("/orders/purchases")
    public int deletePurchases(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam(name = "ids", required = false) List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return orderManagementService.deleteAllProviderPurchases(actor.getProviderId());
        }
        return orderManagementService.deleteProviderPurchases(actor.getProviderId(), ids);
    }

    @DeleteMapping("/orders/rentals")
    public int deleteAllRentals(@AuthenticationPrincipal MarketUserPrincipal actor) {
        return orderManagementService.deleteAllProviderRentals(actor.getProviderId());
    }

    @DeleteMapping("/orders/rentals/{id}")
    public void deleteRental(@AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable Long id) {
        TenantAccess.requireProviderUser(actor);
        orderManagementService.deleteRental(actor, id);
    }

    /**
     * Legacy endpoint used by the current UI during reject flow.
     * It safely "restocks" by adding quantities back to listing stock when possible.
     * This is intentionally tolerant and won't fail on null stock.
     */
    @PutMapping("/listings/update-stock")
    public void updateStock(
            @AuthenticationPrincipal MarketUserPrincipal actor, @Valid @RequestBody List<StockUpdateItem> items) {
        TenantAccess.requireProviderUser(actor);
        if (items == null) return;
        for (var it : items) {
            if (it == null || it.listingId() == null || it.quantity() == null) continue;
            Listing l = listingRepository.findById(it.listingId()).orElse(null);
            if (l == null || l.getProvider() == null || !l.getProvider().getId().equals(actor.getProviderId())) continue;
            Integer cur = l.getStockQuantity();
            if (cur == null) continue;
            l.setStockQuantity(cur + it.quantity());
            listingRepository.save(l);
        }
    }

    @PostMapping("/verify/order/{code}")
    public OrderVerificationResponse verifyPurchaseCode(
            @AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable String code) {
        TenantAccess.requireProviderUser(actor);
        Order o = orderRepository.findByVerificationCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CODE", "Order not found for this code"));
        if (o.getProvider() == null || !o.getProvider().getId().equals(actor.getProviderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROVIDER", "Not your order");
        }
        o.setVerifiedAt(Instant.now());
        orderRepository.save(o);
        return OrderVerificationResponse.from(o, "Order verified");
    }

    @PostMapping("/verify/booking/{code}")
    public BookingVerificationResponse verifyBookingCode(
            @AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable String code) {
        TenantAccess.requireProviderUser(actor);
        RentalBooking b = rentalBookingRepository.findByVerificationCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CODE", "Booking not found for this code"));
        if (b.getProvider() == null || !b.getProvider().getId().equals(actor.getProviderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROVIDER", "Not your booking");
        }
        b.setVerifiedAt(Instant.now());
        rentalBookingRepository.save(b);
        return BookingVerificationResponse.from(b, "Booking verified");
    }
}

