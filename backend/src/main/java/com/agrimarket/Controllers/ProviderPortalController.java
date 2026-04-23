package com.agrimarket.api;

import com.agrimarket.api.dto.BookingVerificationResponse;
import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ListingUpsertRequest;
import com.agrimarket.api.dto.OrderVerificationResponse;
import com.agrimarket.api.dto.ProviderDashboardStatsResponse;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.PurchaseOrder;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.PurchaseOrderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.OrderManagementService;
import com.agrimarket.service.ProviderListingService;
import com.agrimarket.service.TenantAccess;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/provider/me")
@RequiredArgsConstructor
public class ProviderPortalController {

        private final ProviderListingService providerListingService;
        private final OrderManagementService orderManagementService;
        private final PurchaseOrderRepository purchaseOrderRepository;
        private final RentalBookingRepository rentalBookingRepository;
        private final PaymentRecordRepository paymentRecordRepository;

        @GetMapping("/listings")
        public Page<ListingResponse> listings(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                TenantAccess.requireProviderUser(user);
                return providerListingService.listForProvider(user,
                                PageRequest.of(page, size, Sort.by("id").descending()));
        }

        @PostMapping(value = "/listings", consumes = MediaType.APPLICATION_JSON_VALUE)
        public ListingResponse createListing(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @Valid @RequestBody ListingUpsertRequest req) {
                TenantAccess.requireProviderUser(user);
                return providerListingService.create(user, req);
        }

        /**
         * Multipart create — path is {@code /listing-with-images} (not under
         * {@code /listings/...}) so it never collides
         * with {@code /listings/{id}} patterns in any Spring version.
         */
        /**
         * No {@code consumes = multipart}: browsers send
         * {@code multipart/form-data; boundary=...; charset=UTF-8}, which can fail
         * strict {@code consumes} matching and fall through to static resources ("No
         * static resource ...").
         */
        @PostMapping("/listing-with-images")
        public ListingResponse createListingWithImages(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @RequestPart("listing") @Valid ListingUpsertRequest listing,
                        @RequestPart(value = "files", required = false) List<MultipartFile> files) {
                TenantAccess.requireProviderUser(user);
                return providerListingService.createWithImages(user, listing, files);
        }

        /**
         * Listing id must be numeric ({@code \\d+}) so only real ids match (not literal
         * path segments).
         */
        @PutMapping(value = "/listings/{id:\\d+}", consumes = MediaType.APPLICATION_JSON_VALUE)
        public ListingResponse updateListing(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @PathVariable("id") Long id,
                        @Valid @RequestBody ListingUpsertRequest req) {
                TenantAccess.requireProviderUser(user);
                return providerListingService.update(user, id, req);
        }

        @PutMapping("/listings/{id:\\d+}/with-images")
        public ListingResponse updateListingWithImages(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @PathVariable("id") Long id,
                        @RequestPart("listing") @Valid ListingUpsertRequest listing,
                        @RequestPart(value = "files", required = false) List<MultipartFile> files) {
                TenantAccess.requireProviderUser(user);
                return providerListingService.updateWithImages(user, id, listing, files);
        }

        @DeleteMapping("/listings/{id:\\d+}")
        public void deleteListing(@AuthenticationPrincipal MarketUserPrincipal user, @PathVariable("id") Long id) {
                TenantAccess.requireProviderUser(user);
                providerListingService.delete(user, id);
        }

        @GetMapping("/orders/purchases")
        @Transactional(readOnly = true)
        public Page<Map<String, Object>> purchaseOrders(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                TenantAccess.requireProviderUser(user);
                return purchaseOrderRepository
                                .findByProvider_IdOrderByCreatedAtDesc(user.getProviderId(), PageRequest.of(page, size))
                                .map(ProviderPortalController::toPurchaseSummary);
        }

        @GetMapping("/orders/rentals")
        @Transactional(readOnly = true)
        public Page<Map<String, Object>> rentalBookings(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                TenantAccess.requireProviderUser(user);
                return rentalBookingRepository
                                .findByProvider_IdOrderByCreatedAtDesc(user.getProviderId(), PageRequest.of(page, size))
                                .map(ProviderPortalController::toRentalSummary);
        }

        @GetMapping("/dashboard/stats")
        @Transactional(readOnly = true)
        public ProviderDashboardStatsResponse dashboardStats(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
                TenantAccess.requireProviderUser(user);

                // Default: last 7 days (including today) if nothing supplied
                ZoneId zone = ZoneId.systemDefault();
                LocalDate today = LocalDate.now(zone);
                LocalDate effectiveFrom = (from != null) ? from : today.minusDays(6);
                LocalDate effectiveTo = (to != null) ? to : today;

                Instant fromInstant = effectiveFrom.atStartOfDay(zone).toInstant();
                Instant toExclusive = effectiveTo.plusDays(1).atStartOfDay(zone).toInstant();

                long purchasesCount = purchaseOrderRepository.countForProviderBetween(user.getProviderId(), fromInstant,
                                toExclusive);
                BigDecimal purchasesTotal = purchaseOrderRepository.sumTotalForProviderBetween(user.getProviderId(),
                                fromInstant, toExclusive);

                long rentalsCount = rentalBookingRepository.countForProviderBetween(user.getProviderId(), fromInstant,
                                toExclusive);
                BigDecimal rentalsTotal = rentalBookingRepository.sumTotalForProviderBetween(user.getProviderId(),
                                fromInstant, toExclusive);

                return new ProviderDashboardStatsResponse(
                                effectiveFrom, effectiveTo, purchasesCount, purchasesTotal, rentalsCount, rentalsTotal);
        }

        private static Map<String, Object> toPurchaseSummary(PurchaseOrder o) {
                return Map.of(
                                "id", o.getId(),
                                "guestEmail", o.getGuestEmail(),
                                "status", o.getStatus().name(),
                                "guestPhone", o.getGuestPhone(),
                                "guestName", o.getGuestName(),
                                "total", o.getTotalAmount(),
                                "createdAt", o.getCreatedAt().toString());
        }

        private static Map<String, Object> toRentalSummary(RentalBooking b) {
                return Map.of(
                                "id", b.getId(),
                                "listingId", b.getListing().getId(),
                                "guestEmail", b.getGuestEmail(),
                                "createdAt", b.getCreatedAt().toString(),
                                "guestName", b.getGuestName(),
                                "guestPhone", b.getGuestPhone(),
                                "status", b.getStatus().name(),
                                "total", b.getTotalAmount(),
                                "startAt", b.getStartAt().toString(),
                                "endAt", b.getEndAt().toString());
        }

        // Order Management Endpoints
        @GetMapping("/orders/purchases/{id}")
        @Transactional(readOnly = true)
        public Map<String, Object> getPurchaseOrderDetails(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @PathVariable("id") Long id) {
                TenantAccess.requireProviderUser(user);
                PurchaseOrder order = orderManagementService.getOrderById(user.getProviderId(), id);

                // Ensure item lines are loaded and returned in a simple JSON-friendly form.
                List<Map<String, Object>> lines = new ArrayList<>();
                order.getLines().forEach(line -> {
                        Map<String, Object> lineData = new LinkedHashMap<>();
                        lineData.put("id", line.getId());
                        lineData.put("listingTitleSnapshot", line.getListingTitleSnapshot());
                        lineData.put("quantity", line.getQuantity());
                        lineData.put("unitPrice", line.getUnitPrice());
                        lines.add(lineData);
                });

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("id", order.getId());
                response.put("guestName", order.getGuestName());
                response.put("guestEmail", order.getGuestEmail());
                response.put("guestPhone", order.getGuestPhone());
                response.put("deliveryOrPickup", order.getDeliveryOrPickup());
                response.put("status", order.getStatus().name());
                response.put("total", order.getTotalAmount());
                response.put("deliveryDistanceKm", order.getDeliveryDistanceKm());
                response.put("deliveryFee", order.getDeliveryFee());
                response.put("createdAt", order.getCreatedAt().toString());
                response.put("verificationCode", order.getVerificationCode());
                response.put("verifiedAt", order.getVerifiedAt() != null ? order.getVerifiedAt().toString() : null);
                response.put("inventoryFinalized", order.isInventoryFinalized());
                response.put("lines", lines);
                return response;
        }

        @PutMapping("/orders/purchases/{id}/status")
        @Transactional
        public PurchaseOrder updateOrderStatus(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @PathVariable("id") Long id,
                        @RequestParam("status") OrderStatus status) {
                TenantAccess.requireProviderUser(user);
                return orderManagementService.updateOrderStatus(user.getProviderId(), id, status);
        }

        @PostMapping("/orders/purchases/{id}/cancel")
        @Transactional
        public Map<String, String> cancelOrder(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @PathVariable("id") Long id) {
                TenantAccess.requireProviderUser(user);
                orderManagementService.cancelOrder(user.getProviderId(), id);
                return Map.of("message", "Order cancelled successfully");
        }

        @DeleteMapping("/orders/purchases/{id}")
        @Transactional
        public Map<String, String> deleteOrder(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @PathVariable("id") Long id) {
                TenantAccess.requireProviderUser(user);
                orderManagementService.deleteOrder(user, id);
                return Map.of("message", "Order deleted successfully");
        }

        // Rental Booking Detail Endpoint
        @GetMapping("/orders/rentals/{id}")
        @Transactional(readOnly = true)
        public RentalBooking getRentalBookingDetails(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @PathVariable("id") Long id) {
                TenantAccess.requireProviderUser(user);
                RentalBooking booking = rentalBookingRepository.findById(id)
                                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.NOT_FOUND,
                                                "Rental booking not found"));

                if (!booking.getProvider().getId().equals(user.getProviderId())) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.FORBIDDEN, "Access denied");
                }

                return booking;
        }

        // Verification Code Endpoints
        @PostMapping("/verify/order/{code}")
        @Transactional
        public OrderVerificationResponse verifyPurchaseOrder(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @PathVariable("code") String code) {
                TenantAccess.requireProviderUser(user);

                PurchaseOrder order = purchaseOrderRepository.findByVerificationCode(code)
                                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.NOT_FOUND,
                                                "Invalid verification code"));

                if (!order.getProvider().getId().equals(user.getProviderId())) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.FORBIDDEN,
                                        "This order belongs to a different provider");
                }

                if (order.getVerifiedAt() != null) {
                        return OrderVerificationResponse.from(order, "Code already recorded for this order");
                }

                order.setVerifiedAt(Instant.now());
                purchaseOrderRepository.save(order);

                return OrderVerificationResponse.from(order,
                                "Guest verified — you can now confirm payment when funds are received.");
        }

        @PostMapping("/verify/booking/{code}")
        @Transactional
        public BookingVerificationResponse verifyRentalBooking(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @PathVariable("code") String code) {
                TenantAccess.requireProviderUser(user);

                RentalBooking booking = rentalBookingRepository.findByVerificationCode(code)
                                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.NOT_FOUND,
                                                "Invalid verification code"));

                if (!booking.getProvider().getId().equals(user.getProviderId())) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.FORBIDDEN,
                                        "This booking belongs to a different provider");
                }

                if (booking.getVerifiedAt() != null && booking.getStatus() == BookingStatus.CONFIRMED) {
                        return BookingVerificationResponse.from(booking, "Booking already verified");
                }

                if (booking.getVerifiedAt() == null) {
                        booking.setVerifiedAt(Instant.now());
                }
                booking.setStatus(BookingStatus.CONFIRMED);
                paymentRecordRepository
                                .findByRentalBooking_Id(booking.getId())
                                .ifPresent(p -> {
                                        p.setStatus(PaymentStatus.COMPLETED);
                                        paymentRecordRepository.save(p);
                                });
                rentalBookingRepository.save(booking);

                return BookingVerificationResponse.from(booking, "Booking verified successfully");
        }

        // Bulk Delete Endpoints
        @DeleteMapping("/orders/purchases")
        @Transactional
        public Map<String, Object> deletePurchases(
                        @AuthenticationPrincipal MarketUserPrincipal user,
                        @RequestParam(value = "ids", required = false) List<Long> ids) {
                TenantAccess.requireProviderUser(user);
                int deletedCount = orderManagementService.deleteProviderPurchases(user.getProviderId(), ids);
                return Map.of(
                                "deletedCount", deletedCount,
                                "message", ids == null || ids.isEmpty()
                                                ? "All eligible purchase orders deleted successfully"
                                                : "Selected purchase orders deleted successfully");
        }

        @DeleteMapping("/orders/rentals")
        @Transactional
        public Map<String, Object> deleteAllRentals(
                        @AuthenticationPrincipal MarketUserPrincipal user) {
                TenantAccess.requireProviderUser(user);
                int deletedCount = orderManagementService.deleteAllProviderRentals(user.getProviderId());
                return Map.of(
                                "deletedCount", deletedCount,
                                "message", "All eligible rental bookings deleted successfully");
        }
}
