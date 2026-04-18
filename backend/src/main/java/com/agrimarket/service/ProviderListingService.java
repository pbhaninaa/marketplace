package com.agrimarket.service;

import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ListingUpsertRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Provider;
import com.agrimarket.repo.CategoryRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProviderListingService {

    private final ListingRepository listingRepository;
    private final ProviderRepository providerRepository;
    private final CategoryRepository categoryRepository;
    private final SubscriptionService subscriptionService;
    private final SlugService slugService;
    private final ProviderListingImageService providerListingImageService;

    @Transactional(readOnly = true)
    public Page<ListingResponse> listForProvider(MarketUserPrincipal user, Pageable pageable) {
        Provider p = providerRepository
                .findById(user.getProviderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        return listingRepository
                .findAll(ListingSpecifications.byProvider(p.getId(), null), pageable)
                .map(ListingMapper::toResponse);
    }

    @Transactional
    public ListingResponse create(MarketUserPrincipal user, ListingUpsertRequest req) {
        TenantAccess.assertCanWriteListings(user);
        assertSubscription(user.getProviderId(), req.active());
        Provider p = providerRepository
                .findById(user.getProviderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        Category c = resolveCategory(req.categoryName());
        Listing l = new Listing();
        l.setProvider(p);
        l.setCategory(c);
        apply(l, req);
        listingRepository.save(l);
        return ListingMapper.toResponse(l);
    }

    /** Create listing after persisting any uploaded image parts; merges new URLs with {@code req.imageUrls()}. */
    @Transactional
    public ListingResponse createWithImages(
            MarketUserPrincipal user, ListingUpsertRequest req, List<MultipartFile> files) {
        List<String> uploaded = providerListingImageService.saveImages(user.getProviderId(), files);
        String merged = mergeImageUrls(req.imageUrls(), uploaded);
        ListingUpsertRequest mergedReq = new ListingUpsertRequest(
                req.listingType(),
                req.title(),
                req.description(),
                merged,
                req.unitPrice(),
                req.stockQuantity(),
                req.rentPriceHourly(),
                req.rentPriceDaily(),
                req.rentPriceWeekly(),
                req.categoryName(),
                req.active());
        return create(user, mergedReq);
    }

    @Transactional
    public ListingResponse update(MarketUserPrincipal user, Long listingId, ListingUpsertRequest req) {
        TenantAccess.assertCanWriteListings(user);
        assertSubscription(user.getProviderId(), req.active());
        Listing l = listingRepository
                .findAll(ListingSpecifications.byProviderAndId(user.getProviderId(), listingId), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING", "Listing not found"));
        Category c = resolveCategory(req.categoryName());
        l.setCategory(c);
        apply(l, req);
        return ListingMapper.toResponse(listingRepository.save(l));
    }

    @Transactional
    public ListingResponse updateWithImages(
            MarketUserPrincipal user, Long listingId, ListingUpsertRequest req, List<MultipartFile> files) {
        List<String> uploaded = providerListingImageService.saveImages(user.getProviderId(), files);
        String merged = mergeImageUrls(req.imageUrls(), uploaded);
        ListingUpsertRequest mergedReq = new ListingUpsertRequest(
                req.listingType(),
                req.title(),
                req.description(),
                merged,
                req.unitPrice(),
                req.stockQuantity(),
                req.rentPriceHourly(),
                req.rentPriceDaily(),
                req.rentPriceWeekly(),
                req.categoryName(),
                req.active());
        return update(user, listingId, mergedReq);
    }

    @Transactional
    public void delete(MarketUserPrincipal user, Long listingId) {
        TenantAccess.assertCanWriteListings(user);
        Listing l = listingRepository
                .findAll(ListingSpecifications.byProviderAndId(user.getProviderId(), listingId), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING", "Listing not found"));
        listingRepository.delete(l);
    }

    private Category resolveCategory(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY", "Category is required");
        }
        if (name.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY", "Category is too long");
        }
        return categoryRepository
                .findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    String slug = slugService.uniqueCategorySlug(name);
                    Category c = new Category(name, slug);
                    return categoryRepository.save(c);
                });
    }

    private void assertSubscription(Long providerId, boolean wantActive) {
        if (wantActive && !subscriptionService.hasActiveSubscription(providerId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SUBSCRIPTION_REQUIRED",
                    "Active subscription required to publish listings.");
        }
    }

    private static String mergeImageUrls(String existing, List<String> newUrls) {
        List<String> parts = new ArrayList<>();
        if (existing != null && !existing.isBlank()) {
            for (String s : existing.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    parts.add(t);
                }
            }
        }
        if (newUrls != null) {
            for (String u : newUrls) {
                if (u != null && !u.isBlank()) {
                    parts.add(u.trim());
                }
            }
        }
        return String.join(",", parts);
    }

    private static void apply(Listing l, ListingUpsertRequest req) {
        l.setListingType(req.listingType());
        l.setTitle(req.title());
        l.setDescription(req.description());
        l.setImageUrls(req.imageUrls());
        l.setUnitPrice(req.unitPrice());
        l.setStockQuantity(req.stockQuantity());
        l.setRentPriceHourly(req.rentPriceHourly());
        l.setRentPriceDaily(req.rentPriceDaily());
        l.setRentPriceWeekly(req.rentPriceWeekly());
        l.setActive(req.active());
    }
}
