package com.agrimarket.api;

import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ProviderResponse;
import com.agrimarket.api.dto.StaffMemberResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.AdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/providers/{providerId}")
@RequiredArgsConstructor
public class AdminProviderDetailController {

    private final ProviderRepository providerRepository;
    private final AdminService adminService;

    private void requireAdmin(MarketUserPrincipal actor) {
        if (actor == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        if (actor.getRole() != UserRole.PLATFORM_ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "AUTH", "Forbidden");
    }

    @GetMapping
    public ProviderResponse getProvider(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long providerId) {
        requireAdmin(actor);
        var p = providerRepository.findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        return new ProviderResponse(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getLocation(), p.getStatus());
    }

    @GetMapping("/listings")
    public List<ListingResponse> getListings(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long providerId) {
        requireAdmin(actor);
        return adminService.listProviderListings(providerId);
    }

    @GetMapping("/staff")
    public List<StaffMemberResponse> getStaff(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long providerId) {
        requireAdmin(actor);
        return adminService.listProviderStaff(providerId);
    }

    @DeleteMapping("/listings/{listingId}")
    public void deleteListing(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long providerId,
            @PathVariable Long listingId) {
        requireAdmin(actor);
        adminService.deleteProviderListing(providerId, listingId);
    }

    @PatchMapping("/staff/{userId}/disable")
    public void disableStaff(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long providerId,
            @PathVariable Long userId) {
        requireAdmin(actor);
        adminService.disableProviderStaff(providerId, userId);
    }
}

