package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Provider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Prices delivery on the server from two map points, so a buyer can't lower the fee by typing a
 * shorter distance. Distance = straight line between the seller's pickup pin and the buyer's delivery
 * pin x a road factor (roads are longer than a straight line), rounded up to 0.1 km.
 */
@Service
public class DeliveryPricing {

    static final double EARTH_RADIUS_KM = 6371.0088;

    private final double roadFactor;
    private final double maxKm;

    public DeliveryPricing(
            @Value("${app.delivery.road-factor:1.3}") double roadFactor,
            @Value("${app.delivery.max-km:1000}") double maxKm) {
        this.roadFactor = roadFactor > 0 ? roadFactor : 1.3;
        this.maxKm = maxKm > 0 ? maxKm : 1000;
    }

    public record Quote(BigDecimal distanceKm, BigDecimal fee, BigDecimal ratePerKm) {}

    /** True when this seller can deliver: delivery on, a rate per km, and a pickup pin set. */
    public static boolean offersDelivery(Provider p) {
        return p != null
                && p.isDeliveryAvailable()
                && p.getDeliveryPricePerKm() != null
                && p.getDeliveryPricePerKm().signum() > 0
                && validPoint(p.getPickupLatitude(), p.getPickupLongitude());
    }

    /** Distance and fee from the seller's pickup pin to the buyer's pin. Throws a clear 400 if not possible. */
    public Quote quote(Provider seller, Double buyerLat, Double buyerLng) {
        if (seller == null || !seller.isDeliveryAvailable()
                || seller.getDeliveryPricePerKm() == null || seller.getDeliveryPricePerKm().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DELIVERY_UNAVAILABLE", "This seller does not offer delivery.");
        }
        if (!validPoint(seller.getPickupLatitude(), seller.getPickupLongitude())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DELIVERY_UNAVAILABLE",
                    "This seller hasn't set their pickup location yet, so only pickup is possible.");
        }
        if (!validPoint(buyerLat, buyerLng)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DELIVERY_LOCATION_REQUIRED",
                    "Choose your delivery location on the map.");
        }
        double km = roadKm(seller.getPickupLatitude(), seller.getPickupLongitude(), buyerLat, buyerLng);
        if (km > maxKm) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DELIVERY_TOO_FAR",
                    "That location is too far for delivery. Choose pickup or a closer address.");
        }
        BigDecimal distance = BigDecimal.valueOf(km).setScale(1, RoundingMode.CEILING);
        BigDecimal rate = seller.getDeliveryPricePerKm();
        BigDecimal fee = rate.multiply(distance).setScale(2, RoundingMode.HALF_UP);
        return new Quote(distance, fee, rate);
    }

    /** Straight-line (great-circle) distance in km x road factor. */
    double roadKm(double lat1, double lng1, double lat2, double lng2) {
        return haversineKm(lat1, lng1, lat2, lng2) * roadFactor;
    }

    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    static boolean validPoint(Double lat, Double lng) {
        return lat != null && lng != null
                && !lat.isNaN() && !lng.isNaN()
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                && !(lat == 0 && lng == 0);
    }
}
