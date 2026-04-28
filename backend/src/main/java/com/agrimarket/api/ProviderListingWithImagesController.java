package com.agrimarket.api;

import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ListingUpsertRequest;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderListingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/provider/me")
@RequiredArgsConstructor
public class ProviderListingWithImagesController {

    private final ProviderListingService providerListingService;

    /**
     * Frontend uses this route when creating a listing with images.
     * Expects multipart with:
     * - part "listing" (application/json) -> ListingUpsertRequest
     * - part "files" (0..n) -> image files
     */
    @PostMapping(path = "/listing-with-images", consumes = {"multipart/form-data"})
    public ListingResponse createWithImages(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @Valid @RequestPart("listing") ListingUpsertRequest listing,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return providerListingService.createWithImages(user, listing, files);
    }
}

