package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Provider;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeliveryPricingTest {

    // Johannesburg (Park Station) and Pretoria (Church Square): about 54 km apart in a straight line.
    static final double JHB_LAT = -26.1952, JHB_LNG = 28.0423;
    static final double PTA_LAT = -25.7461, PTA_LNG = 28.1881;

    private final DeliveryPricing pricing = new DeliveryPricing(1.3, 1000);

    private static Provider seller(Double lat, Double lng, boolean delivery, String rate) {
        Provider p = new Provider("Farm", "farm", "Test", "Testville");
        p.setDeliveryAvailable(delivery);
        p.setDeliveryPricePerKm(rate == null ? null : new BigDecimal(rate));
        p.setPickupLatitude(lat);
        p.setPickupLongitude(lng);
        return p;
    }

    @Test
    void straightLineDistanceMatchesRealWorld() {
        double km = DeliveryPricing.haversineKm(JHB_LAT, JHB_LNG, PTA_LAT, PTA_LNG);
        assertThat(km).isBetween(50.0, 56.0);
    }

    @Test
    void feeIsRoadDistanceTimesRateRoundedUpToTenthOfAKm() {
        var q = pricing.quote(seller(JHB_LAT, JHB_LNG, true, "10"), PTA_LAT, PTA_LNG);
        double expectedKm = DeliveryPricing.haversineKm(JHB_LAT, JHB_LNG, PTA_LAT, PTA_LNG) * 1.3;
        assertThat(q.distanceKm().doubleValue()).isBetween(expectedKm, expectedKm + 0.1);
        assertThat(q.fee()).isEqualByComparingTo(q.distanceKm().multiply(new BigDecimal("10")));
    }

    @Test
    void deliveryNeedsTheSellersPinRateAndTheBuyersPin() {
        assertThat(DeliveryPricing.offersDelivery(seller(JHB_LAT, JHB_LNG, true, "10"))).isTrue();
        assertThat(DeliveryPricing.offersDelivery(seller(null, null, true, "10"))).isFalse();
        assertThat(DeliveryPricing.offersDelivery(seller(JHB_LAT, JHB_LNG, false, "10"))).isFalse();
        assertThat(DeliveryPricing.offersDelivery(seller(JHB_LAT, JHB_LNG, true, null))).isFalse();

        assertThatThrownBy(() -> pricing.quote(seller(null, null, true, "10"), PTA_LAT, PTA_LNG))
                .isInstanceOf(ApiException.class).hasMessageContaining("pickup location");
        assertThatThrownBy(() -> pricing.quote(seller(JHB_LAT, JHB_LNG, true, "10"), null, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("delivery location");
        // 0,0 is "no location" (a common bad default), not a point in the sea off Africa.
        assertThatThrownBy(() -> pricing.quote(seller(JHB_LAT, JHB_LNG, true, "10"), 0.0, 0.0))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void tooFarIsRefused() {
        DeliveryPricing shortRange = new DeliveryPricing(1.3, 20);
        assertThatThrownBy(() -> shortRange.quote(seller(JHB_LAT, JHB_LNG, true, "10"), PTA_LAT, PTA_LNG))
                .isInstanceOf(ApiException.class).hasMessageContaining("too far");
    }
}
