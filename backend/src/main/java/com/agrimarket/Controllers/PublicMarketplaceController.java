package com.agrimarket.api;

import com.agrimarket.api.dto.ListingFilterParams;
import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ProviderOptionResponse;
import com.agrimarket.api.dto.CategoryOptionResponse;
import com.agrimarket.api.dto.ProviderResponse;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.ListingType;
import com.agrimarket.domain.Provider;
import com.agrimarket.repo.CategoryRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.service.ListingMapper;
import com.agrimarket.service.ListingSpecifications;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMarketplaceController {

    private final ListingRepository listingRepository;
    private final ProviderRepository providerRepository;
    private final CategoryRepository categoryRepository;

    @GetMapping("/listings")
    @Transactional(readOnly = true)
    public Page<ListingResponse> listings(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) ListingType listingType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var filter = new ListingFilterParams(categoryId, providerId, listingType, minPrice, maxPrice, location, search);
        return listingRepository
                .findAll(ListingSpecifications.publicFeed(filter), PageRequest.of(page, size, Sort.by("id").descending()))
                .map(ListingMapper::toResponse);
    }

    @GetMapping("/providers")
    public Page<ProviderResponse> providers(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return providerRepository
                .findAll(PageRequest.of(page, size, Sort.by("name")))
                .map(p -> new ProviderResponse(
                        p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getLocation(), p.getStatus()));
    }

    /** Filter providers list to only those with active listings (optional listingType). */
    @GetMapping("/provider-options")
    @Transactional(readOnly = true)
    public List<ProviderOptionResponse> providerOptions(@RequestParam(required = false) ListingType listingType) {
        return listingRepository
                .findAll(ListingSpecifications.publicFeed(new ListingFilterParams(
                        null, null, listingType, null, null, null, null)))
                .stream()
                .map(l -> l.getProvider())
                .filter(p -> p != null && p.getStatus() == ProviderStatus.ACTIVE)
                .collect(java.util.stream.Collectors.toMap(
                        p -> p.getId(),
                        p -> p,
                        (a, b) -> a))
                .values()
                .stream()
                .sorted(java.util.Comparator.comparing(Provider::getName))
                .map(p -> new ProviderOptionResponse(p.getId(), p.getName()))
                .toList();
    }

    /** Filter categories list to only those with active listings (optional listingType). */
    @GetMapping("/category-options")
    @Transactional(readOnly = true)
    public List<CategoryOptionResponse> categoryOptions(@RequestParam(required = false) ListingType listingType) {
        return listingRepository
                .findAll(ListingSpecifications.publicFeed(new ListingFilterParams(
                        null, null, listingType, null, null, null, null)))
                .stream()
                .map(l -> l.getCategory())
                .filter(c -> c != null)
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.getId(),
                        c -> c,
                        (a, b) -> a))
                .values()
                .stream()
                .sorted(java.util.Comparator.comparing(com.agrimarket.domain.Category::getName))
                .map(c -> new CategoryOptionResponse(c.getId(), c.getName()))
                .toList();
    }

    @GetMapping("/providers/{slug}")
    public ProviderResponse providerBySlug(@PathVariable String slug) {
        var p = providerRepository
                .findBySlug(slug)
                .orElseThrow(() -> new com.agrimarket.api.error.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "PROVIDER", "Not found"));
        return new ProviderResponse(
                p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getLocation(), p.getStatus());
    }

    @GetMapping("/categories")
    public java.util.List<com.agrimarket.domain.Category> categories() {
        return categoryRepository.findAll(Sort.by("name"));
    }
}
