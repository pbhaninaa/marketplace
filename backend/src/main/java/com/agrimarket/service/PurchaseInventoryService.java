package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import com.agrimarket.domain.OrderLine;
import com.agrimarket.domain.PurchaseOrder;
import com.agrimarket.repo.CartLineRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.OrderLineRepository;
import com.agrimarket.repo.RentalBookingRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves sale stock when a guest places an order (unpaid), finalizes deduction when payment is confirmed,
 * or releases reservation if the order is cancelled before payment.
 */
@Service
@RequiredArgsConstructor
public class PurchaseInventoryService {

    private final ListingRepository listingRepository;
    private final OrderLineRepository orderLineRepository;
    private final CartLineRepository cartLineRepository;
    private final RentalBookingRepository rentalBookingRepository;

    @Transactional
    public void finalizePaidPurchase(PurchaseOrder order) {
        if (order.isInventoryFinalized()) {
            return;
        }
        Set<Long> touchedListingIds = new LinkedHashSet<>();
        for (OrderLine line : order.getLines()) {
            if (line.getListing() == null) {
                continue;
            }
            Long listingId = line.getListing().getId();
            touchedListingIds.add(listingId);
            Listing l = listingRepository.findByIdWithLock(listingId).orElseThrow();
            if (l.getStockQuantity() == null) {
                continue;
            }
            int qty = line.getQuantity();
            int reserved = l.getReservedStock() == null ? 0 : l.getReservedStock();
            int newStock = l.getStockQuantity() - qty;
            if (newStock < 0) {
                throw new ApiException(
                        HttpStatus.CONFLICT, "STOCK", "Stock would go negative for listing " + listingId);
            }
            l.setStockQuantity(newStock);
            l.setReservedStock(Math.max(0, reserved - qty));
            listingRepository.save(l);
        }
        order.setInventoryFinalized(true);

        for (Long listingId : touchedListingIds) {
            listingRepository
                    .findById(listingId)
                    .ifPresent(this::deleteDepletedSaleListingIfEligible);
        }
    }

    /**
     * When on-hand and reserved are both zero for a sale listing, remove the catalog row and detach order/cart refs.
     */
    private void deleteDepletedSaleListingIfEligible(Listing l) {
        if (l.getListingType() != ListingType.SALE || l.getStockQuantity() == null) {
            return;
        }
        int sq = l.getStockQuantity();
        int rs = l.getReservedStock() == null ? 0 : l.getReservedStock();
        if (sq != 0 || rs != 0) {
            return;
        }
        if (rentalBookingRepository.existsByListing_Id(l.getId())) {
            return;
        }
        Long id = l.getId();
        cartLineRepository.deleteByListing_Id(id);
        detachOrderLinesFromListing(id);
        listingRepository.delete(l);
    }

    private void detachOrderLinesFromListing(Long listingId) {
        List<OrderLine> refs = orderLineRepository.findByListing_Id(listingId);
        for (OrderLine ol : refs) {
            if (ol.getListingTitleSnapshot() == null && ol.getListing() != null) {
                ol.setListingTitleSnapshot(ol.getListing().getTitle());
            }
            ol.setListing(null);
            orderLineRepository.save(ol);
        }
    }

    @Transactional
    public void releasePendingReservation(PurchaseOrder order) {
        if (order.isInventoryFinalized()) {
            return;
        }
        for (OrderLine line : order.getLines()) {
            if (line.getListing() == null) {
                continue;
            }
            Long listingId = line.getListing().getId();
            Listing l = listingRepository.findByIdWithLock(listingId).orElseThrow();
            if (l.getStockQuantity() == null) {
                continue;
            }
            int qty = line.getQuantity();
            int reserved = l.getReservedStock() == null ? 0 : l.getReservedStock();
            l.setReservedStock(Math.max(0, reserved - qty));
            listingRepository.save(l);
        }
    }
}
