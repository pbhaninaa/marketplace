package com.agrimarket.api;

import com.agrimarket.api.dto.ProviderResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.security.MarketUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/providers")
@RequiredArgsConstructor
public class AdminProvidersController {

    private final ProviderRepository providerRepository;

    public record SetStatusRequest(ProviderStatus status) {}

    @GetMapping
    public Page<ProviderResponse> list(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        if (actor.getRole() != UserRole.PLATFORM_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH", "Forbidden");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return providerRepository.findAll(pageable).map(p -> new ProviderResponse(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getDescription(),
                p.getLocation(),
                p.getStatus()));
    }

    @PatchMapping("/{id}/status")
    public void setStatus(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long id,
            @RequestBody SetStatusRequest req) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        if (actor.getRole() != UserRole.PLATFORM_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH", "Forbidden");
        }
        if (req == null || req.status() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Status is required");
        }
        var p = providerRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        p.setStatus(req.status());
        providerRepository.save(p);
    }
}

