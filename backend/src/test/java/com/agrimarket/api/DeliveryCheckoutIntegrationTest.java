package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Delivery is priced by the server from map pins; the buyer can't set the distance. */
class DeliveryCheckoutIntegrationTest extends AbstractIntegrationTest {

    // Seller in Johannesburg, buyer in Pretoria (about 54 km straight line, about 70 km by the 1.3 road factor).
    static final double JHB_LAT = -26.1952, JHB_LNG = 28.0423;
    static final String PTA_LAT = "-25.7461", PTA_LNG = "28.1881";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private RentalBookingRepository rentalBookingRepository;

    private Provider seller;
    private Listing hay;
    private Listing tractor;
    private String session;

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        session = "deliver-" + u;
        Category cat = fixtures.saveCategory("Farm", "farm-" + u);
        seller = fixtures.saveActiveProvider("Deliver Co", "deliver-" + u);
        seller.setDeliveryAvailable(true);
        seller.setDeliveryPricePerKm(new BigDecimal("10.00"));
        seller.setPickupLatitude(JHB_LAT);
        seller.setPickupLongitude(JHB_LNG);
        providerRepository.save(seller);
        fixtures.saveActiveSubscription(seller);
        hay = fixtures.saveSaleListing(seller, cat, "Hay", new BigDecimal("100"), 20);
        tractor = fixtures.saveRentListing(seller, cat, "Tractor", new BigDecimal("500"));
    }

    private void add(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", session)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private ResultActions checkout(String mode, String lat, String lng, String typedKm) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guestName", "Test Guest");
        body.put("guestEmail", "guest+" + UUID.randomUUID() + "@deliver.test");
        body.put("guestPhone", "0820000000");
        body.put("deliveryOrPickup", mode);
        body.put("paymentMethod", "CASH");
        if (lat != null) body.put("latitude", lat);
        if (lng != null) body.put("longitude", lng);
        if (typedKm != null) body.put("deliveryDistanceKm", new BigDecimal(typedKm));
        body.put("deliveryAddress", "12 Church Street, Pretoria");
        return mockMvc.perform(post("/api/public/cart/checkout")
                .header("X-Session-Id", session)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void theFeeComesFromTheMapPinsNotTheDistanceTheBuyerTypes() throws Exception {
        add(Map.of("listingId", hay.getId(), "quantity", 1));
        String body = checkout("DELIVERY", PTA_LAT, PTA_LNG, "1").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(body).at("/purchaseOrderIds/0").asLong();
        Order o = orderRepository.findById(orderId).orElseThrow();

        assertThat(o.getDeliveryDistanceKm().doubleValue()).isBetween(65.0, 75.0); // not the 1 km typed
        assertThat(o.getDeliveryFee()).isEqualByComparingTo(o.getDeliveryDistanceKm().multiply(new BigDecimal("10.00")));
        assertThat(o.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100").add(o.getDeliveryFee()));
        // The seller gets the address and the pin to deliver to.
        assertThat(o.getDeliveryAddress()).isEqualTo("12 Church Street, Pretoria");
        assertThat(o.getDeliveryLatitude()).isEqualTo(-25.7461);
        assertThat(o.getDeliveryLongitude()).isEqualTo(28.1881);
    }

    @Test
    void oneDeliveryFeePerCheckoutEvenWithAnOrderAndARental() throws Exception {
        add(Map.of("listingId", hay.getId(), "quantity", 1));
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        add(Map.of("listingId", tractor.getId(), "quantity", 1,
                "rentalStart", start.toString(), "rentalEnd", start.plus(1, ChronoUnit.DAYS).toString()));

        JsonNode res = objectMapper.readTree(checkout("DELIVERY", PTA_LAT, PTA_LNG, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Order o = orderRepository.findById(res.at("/purchaseOrderIds/0").asLong()).orElseThrow();
        RentalBooking b = rentalBookingRepository.findById(res.at("/rentalBookingIds/0").asLong()).orElseThrow();

        assertThat(o.getDeliveryFee().signum()).isPositive();
        assertThat(b.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO); // not charged twice
        assertThat(b.getLatitude()).isEqualTo("-25.7461");
    }

    @Test
    void deliveryNeedsABuyerPinAndASellerPin() throws Exception {
        add(Map.of("listingId", hay.getId(), "quantity", 1));
        checkout("DELIVERY", null, null, "5")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DELIVERY_LOCATION_REQUIRED"));

        seller.setPickupLatitude(null);
        seller.setPickupLongitude(null);
        providerRepository.save(seller);
        checkout("DELIVERY", PTA_LAT, PTA_LNG, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DELIVERY_UNAVAILABLE"));
        // Without a pin the cart doesn't offer delivery for this seller.
        mockMvc.perform(get("/api/public/cart").header("X-Session-Id", session))
                .andExpect(jsonPath("$.sellers[0].deliveryAvailable").value(false));
        // Pickup still works and has no fee.
        checkout("PICKUP", null, null, "50").andExpect(status().isOk());
    }

    @Test
    void quoteMatchesCheckoutAndHidesTheSellersPin() throws Exception {
        String quote = mockMvc.perform(get("/api/public/cart/delivery-quote")
                        .param("providerId", seller.getId().toString())
                        .param("lat", PTA_LAT)
                        .param("lng", PTA_LNG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee").exists())
                .andExpect(jsonPath("$.pickupLatitude").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        BigDecimal quotedFee = new BigDecimal(objectMapper.readTree(quote).get("fee").asText());

        add(Map.of("listingId", hay.getId(), "quantity", 1));
        long orderId = objectMapper.readTree(checkout("DELIVERY", PTA_LAT, PTA_LNG, null)
                .andReturn().getResponse().getContentAsString()).at("/purchaseOrderIds/0").asLong();
        assertThat(orderRepository.findById(orderId).orElseThrow().getDeliveryFee()).isEqualByComparingTo(quotedFee);
    }
}
