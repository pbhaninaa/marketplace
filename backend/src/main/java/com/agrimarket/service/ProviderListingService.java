package com.agrimarket.service;

import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.ListingUpsertRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.*;
import com.agrimarket.repo.CategoryRepository;
import com.agrimarket.repo.CartLineRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.RentalBookingRepository;
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
    private final PaymentRecordRepository paymentRecordRepository;
    private final RentalBookingRepository rentalBookingRepository;
    private final CartLineRepository cartLineRepository;

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

        // =========================
        // 1. LOAD LISTING
        // =========================
        Listing listing = listingRepository
                .findAll(
                        ListingSpecifications.byProviderAndId(user.getProviderId(), listingId),
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING", "Listing not found"));

        // Always remove any in-progress cart lines referencing this listing (safe to delete).
        // We do this first to prevent "stuck cart" rows and to reduce FK surprises.
        cartLineRepository.deleteActiveCartLinesByListingId(listingId);

        // Safe delete: if a listing is referenced by any existing orders/bookings,
        // we prefer to unpublish rather than hard-delete to avoid FK issues.
        // (Admin maintenance can be used for deeper cleanup.)
        boolean hasBookings = rentalBookingRepository.existsByListing_Id(listingId);
        boolean referencedByOrders = cartLineRepository.existsByListing_IdAndOrderIsNotNull(listingId);
        if (hasBookings || referencedByOrders) {
            listing.setActive(false);
            listingRepository.save(listing);
            return;
        }

        // Remove linked payment records for rentals (if any) then delete rentals and listing.
        List<RentalBooking> rentals = rentalBookingRepository.findAllByListing_Id(listingId);
        for (RentalBooking rental : rentals) {
            paymentRecordRepository.deleteByRentalBooking_Id(rental.getId());
            rentalBookingRepository.delete(rental);
        }

        listingRepository.delete(listing);
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
    // Removed stale stock-restock helpers (were unused and could NPE on null stockQuantity).
}
