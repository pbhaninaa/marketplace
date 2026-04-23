package com.agrimarket.api;

import com.agrimarket.api.dto.AdminProviderStatusRequest;
import com.agrimarket.api.dto.AdminListingActiveRequest;
import com.agrimarket.api.dto.AdminSupportUserResponse;
import com.agrimarket.api.dto.AdminUserResponse;
import com.agrimarket.api.dto.AdminDashboardStatsResponse;
import com.agrimarket.api.dto.CreateSupportUserRequest;
import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ProviderResponse;
import com.agrimarket.api.dto.StaffMemberResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.PurchaseOrderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.AdminService;
import com.agrimarket.service.AdminMaintenanceService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ProviderRepository providerRepository;
    private final AdminMaintenanceService adminMaintenanceService;
    private final UserAccountRepository userAccountRepository;
    private final ListingRepository listingRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final RentalBookingRepository rentalBookingRepository;

    @GetMapping("/dashboard/stats")
    public AdminDashboardStatsResponse dashboardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate effectiveFrom = (from != null) ? from : today.minusDays(6);
        LocalDate effectiveTo = (to != null) ? to : today;

        Instant fromInstant = effectiveFrom.atStartOfDay(zone).toInstant();
        Instant toExclusive = effectiveTo.plusDays(1).atStartOfDay(zone).toInstant();

        long usersCreated = userAccountRepository.countCreatedBetween(fromInstant, toExclusive);
        long providersCreated = providerRepository.countCreatedBetween(fromInstant, toExclusive);
        long listingsCreated = listingRepository.countCreatedBetween(fromInstant, toExclusive);
        long activeListings = listingRepository.countByActiveTrue();

        long purchaseCount = purchaseOrderRepository.countBetween(fromInstant, toExclusive);
        BigDecimal purchaseTotal = purchaseOrderRepository.sumTotalBetween(fromInstant, toExclusive);
        long rentalCount = rentalBookingRepository.countBetween(fromInstant, toExclusive);
        BigDecimal rentalTotal = rentalBookingRepository.sumTotalBetween(fromInstant, toExclusive);

        return new AdminDashboardStatsResponse(
                effectiveFrom,
                effectiveTo,
                usersCreated,
                providersCreated,
                listingsCreated,
                activeListings,
                purchaseCount,
                purchaseTotal,
                rentalCount,
                rentalTotal);
    }

    @PostMapping("/create-support-user")
    public void createSupport(@Valid @RequestBody CreateSupportUserRequest req) {
        adminService.createSupportUser(req);
    }

    @GetMapping("/support-users")
    public List<AdminSupportUserResponse> supportUsers() {
        return adminService.listSupportUsers();
    }

    @GetMapping("/providers")
    public Page<ProviderResponse> providers(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return providerRepository
                .findAll(PageRequest.of(page, size, Sort.by("id").descending()))
                .map(p -> new ProviderResponse(
                        p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getLocation(), p.getStatus()));
    }

    @GetMapping("/providers/{id}")
    public ProviderResponse provider(@PathVariable Long id) {
        var p = providerRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        return new ProviderResponse(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getLocation(), p.getStatus());
    }

    @GetMapping("/providers/{id}/listings")
    public List<ListingResponse> providerListings(@PathVariable Long id) {
        return adminService.listProviderListings(id);
    }

    @DeleteMapping("/providers/{providerId}/listings/{listingId}")
    public void deleteProviderListing(@PathVariable Long providerId, @PathVariable Long listingId) {
        adminService.deleteProviderListing(providerId, listingId);
    }

    @GetMapping("/providers/{id}/staff")
    public List<StaffMemberResponse> providerStaff(@PathVariable Long id) {
        return adminService.listProviderStaff(id);
    }

    @PatchMapping("/providers/{providerId}/staff/{staffUserId}/disable")
    public void disableProviderStaff(@PathVariable Long providerId, @PathVariable Long staffUserId) {
        adminService.disableProviderStaff(providerId, staffUserId);
    }

    @GetMapping("/listings")
    public Page<ListingResponse> listings(Pageable pageable) {
        return adminService.listAllListings(pageable);
    }

    @DeleteMapping("/listings/{id}")
    public void deleteListing(@PathVariable Long id) {
        adminService.deleteListing(id);
    }

    @DeleteMapping("/listings")
    public java.util.Map<String, Object> deleteAllListings() {
        int deleted = adminService.deleteAllListingsSafe();
        return java.util.Map.of("deleted", deleted);
    }

    @PatchMapping("/listings/{id}/active")
    public void setListingActive(@PathVariable Long id, @Valid @RequestBody AdminListingActiveRequest body) {
        adminService.setListingActive(id, body.active());
    }

    @GetMapping("/users")
    public org.springframework.data.domain.Page<AdminUserResponse> users(Pageable pageable) {
        return adminService.listUsers(pageable);
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
    }

    @DeleteMapping("/users")
    public java.util.Map<String, Object> deleteAllUsers(@AuthenticationPrincipal MarketUserPrincipal user) {
        int disabled = adminService.deleteAllUsersExcept(user.getUserId());
        return java.util.Map.of("disabled", disabled);
    }

    @PostMapping("/maintenance/clean-db")
    public java.util.Map<String, Object> cleanDb(@AuthenticationPrincipal MarketUserPrincipal user) {
        return adminMaintenanceService.cleanDbKeepAdmin(user.getUserId());
    }

    @PatchMapping("/providers/{id}/status")
    public void patchStatus(@PathVariable Long id, @Valid @RequestBody AdminProviderStatusRequest body) {
        adminService.updateProviderStatus(id, body.status());
    }
}
