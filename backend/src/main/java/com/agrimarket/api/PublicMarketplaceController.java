package com.agrimarket.api;

import com.agrimarket.api.dto.CategoryOptionResponse;
import com.agrimarket.api.dto.ListingFilterParams;
import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ProviderOptionResponse;
import com.agrimarket.domain.ListingType;
import com.agrimarket.repo.CategoryRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.service.ListingMapper;
import com.agrimarket.service.ListingSpecifications;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMarketplaceController {

    private final ListingRepository listingRepository;
    private final CategoryRepository categoryRepository;
    private final ProviderRepository providerRepository;

    @GetMapping("/categories")
    public java.util.List<CategoryOptionResponse> categories() {
        return categoryRepository.findAll(Sort.by("name").ascending()).stream()
                .map(c -> new CategoryOptionResponse(c.getId(), c.getName()))
                .toList();
    }

    @GetMapping("/category-options")
    public java.util.List<CategoryOptionResponse> categoryOptions(@RequestParam(required = false) ListingType listingType) {
        // simple: always return all categories
        return categoryRepository.findAll(Sort.by("name").ascending()).stream()
                .map(c -> new CategoryOptionResponse(c.getId(), c.getName()))
                .toList();
    }

    @GetMapping("/provider-options")
    public java.util.List<ProviderOptionResponse> providerOptions(@RequestParam(required = false) ListingType listingType) {
        // simple: return all providers (front-end can filter)
        return providerRepository.findAll(Sort.by("name").ascending()).stream()
                .map(p -> new ProviderOptionResponse(p.getId(), p.getName()))
                .toList();
    }

    @GetMapping("/listings")
    public Page<ListingResponse> listings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) ListingType listingType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false, name = "location") String locationContains,
            @RequestParam(required = false, name = "search") String search) {

        var f = new ListingFilterParams(categoryId, providerId, listingType, minPrice, maxPrice, locationContains, search);
        var pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return listingRepository.findAll(ListingSpecifications.publicFeed(f), pageable).map(ListingMapper::toResponse);
    }
}

