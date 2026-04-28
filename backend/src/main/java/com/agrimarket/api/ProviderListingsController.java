package com.agrimarket.api;

import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ListingUpsertRequest;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderListingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping("/api/provider/me/listings")
@RequiredArgsConstructor
public class ProviderListingsController {

    private final ProviderListingService providerListingService;

    @GetMapping
    public Page<ListingResponse> list(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return providerListingService.listForProvider(
                user,
                PageRequest.of(
                        Math.max(0, page),
                        Math.min(Math.max(1, size), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping
    public ListingResponse create(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @Valid @RequestBody ListingUpsertRequest req) {
        return providerListingService.create(user, req);
    }

    @PutMapping("/{id}")
    public ListingResponse update(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long id,
            @Valid @RequestBody ListingUpsertRequest req) {
        return providerListingService.update(user, id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal MarketUserPrincipal user, @PathVariable Long id) {
        providerListingService.delete(user, id);
    }

    /**
     * PUT /api/provider/me/listings/{id}/with-images
     */
    @PutMapping(path = "/{id}/with-images", consumes = {"multipart/form-data"})
    public ListingResponse updateWithImages(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long id,
            @Valid @RequestPart("listing") ListingUpsertRequest listing,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return providerListingService.updateWithImages(user, id, listing, files);
    }
}

