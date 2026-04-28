package com.agrimarket.service;

import com.agrimarket.api.dto.ListingFilterParams;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class ListingSpecifications {

    private ListingSpecifications() {}

    public static Specification<Listing> publicFeed(ListingFilterParams f) {
        return (root, query, cb) -> {
            // Ensure provider/category are initialized for DTO mapping (avoid LazyInitializationException)
            if (query.getResultType() == Listing.class) {
                root.fetch("provider", JoinType.INNER);
                root.fetch("category", JoinType.INNER);
            }
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isTrue(root.get("active")));
            Join<Object, Object> provider = root.join("provider", JoinType.INNER);
            p.add(cb.equal(provider.get("status"), com.agrimarket.domain.ProviderStatus.ACTIVE));
            Join<Object, Object> category = root.join("category", JoinType.INNER);

            if (f.categoryId() != null) {
                p.add(cb.equal(category.get("id"), f.categoryId()));
            }
            if (f.providerId() != null) {
                p.add(cb.equal(provider.get("id"), f.providerId()));
            }
            if (f.listingType() != null) {
                p.add(cb.equal(root.get("listingType"), f.listingType()));
            }
            if (f.minPrice() != null || f.maxPrice() != null) {
                ListingType lt = f.listingType();
                if (lt == ListingType.RENT) {
                    p.add(rentAnyRateInRange(root, cb, f.minPrice(), f.maxPrice()));
                } else if (lt == ListingType.SALE) {
                    p.add(unitPriceInRange(root, cb, f.minPrice(), f.maxPrice()));
                } else {
                    Predicate saleRows = cb.and(
                            cb.equal(root.get("listingType"), ListingType.SALE),
                            unitPriceInRange(root, cb, f.minPrice(), f.maxPrice()));
                    Predicate rentRows = cb.and(
                            cb.equal(root.get("listingType"), ListingType.RENT),
                            rentAnyRateInRange(root, cb, f.minPrice(), f.maxPrice()));
                    p.add(cb.or(saleRows, rentRows));
                }
            }
            if (f.locationContains() != null && !f.locationContains().isBlank()) {
                p.add(cb.like(cb.lower(provider.get("location")), "%" + f.locationContains().toLowerCase() + "%"));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }

            // Sale listings with tracked stock: hide when nothing left to buy (after reservations).
            Predicate salePurchasable = cb.or(
                    cb.notEqual(root.get("listingType"), ListingType.SALE),
                    cb.isNull(root.get("stockQuantity")),
                    cb.greaterThan(
                            cb.diff(
                                    root.get("stockQuantity"),
                                    cb.coalesce(root.get("reservedStock"), cb.literal(0))),
                            0));
            p.add(salePurchasable);

            query.distinct(true);
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** Sale items: filter on {@link Listing#getUnitPrice()} only. */
    private static Predicate unitPriceInRange(Root<Listing> root, CriteriaBuilder cb, BigDecimal min, BigDecimal max) {
        List<Predicate> parts = new ArrayList<>();
        if (min != null) {
            parts.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), min));
        }
        if (max != null) {
            parts.add(cb.lessThanOrEqualTo(root.get("unitPrice"), max));
        }
        return cb.and(parts.toArray(Predicate[]::new));
    }

    /**
     * Rentals: match if any of unit price or hourly/daily/weekly rate falls in the range (whichever is set on the
     * listing).
     */
    private static Predicate rentAnyRateInRange(Root<Listing> root, CriteriaBuilder cb, BigDecimal min, BigDecimal max) {
        List<Predicate> ors = new ArrayList<>();
        for (String col : List.of("unitPrice", "rentPriceHourly", "rentPriceDaily", "rentPriceWeekly")) {
            ors.add(singleColumnInRange(root, cb, col, min, max));
        }
        return cb.or(ors.toArray(Predicate[]::new));
    }

    private static Predicate singleColumnInRange(
            Root<Listing> root, CriteriaBuilder cb, String column, BigDecimal min, BigDecimal max) {
        List<Predicate> parts = new ArrayList<>();
        parts.add(cb.isNotNull(root.get(column)));
        if (min != null) {
            parts.add(cb.greaterThanOrEqualTo(root.get(column), min));
        }
        if (max != null) {
            parts.add(cb.lessThanOrEqualTo(root.get(column), max));
        }
        return cb.and(parts.toArray(Predicate[]::new));
    }

    public static Specification<Listing> byProviderAndId(Long providerId, Long listingId) {
        return (root, query, cb) -> {
            Join<Object, Object> provider = root.join("provider", JoinType.INNER);
            return cb.and(cb.equal(provider.get("id"), providerId), cb.equal(root.get("id"), listingId));
        };
    }

    public static Specification<Listing> byProvider(Long providerId, ListingType type) {
        return (root, query, cb) -> {
            Join<Object, Object> provider = root.join("provider", JoinType.INNER);
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(provider.get("id"), providerId));
            if (type != null) {
                p.add(cb.equal(root.get("listingType"), type));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
