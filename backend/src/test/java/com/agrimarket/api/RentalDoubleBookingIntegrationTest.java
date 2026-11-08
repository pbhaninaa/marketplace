package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.service.PeachPaymentService;
import com.agrimarket.support.TestFixtures;
import com.agrimarket.util.PeachSignatureUtil;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** One rental item can only be booked once for any moment in time. */
@TestPropertySource(properties = {
    // Same Peach test settings as PeachSubscriptionWebhookIntegrationTest (shares its Spring context).
    "peach.enabled=true",
    "peach.client-id=test-client",
    "peach.client-secret=test-secret",
    "peach.merchant-id=test-merchant",
    "peach.entity-id=test-entity",
    "peach.secret-token=webhook-signing-secret"
})
class RentalDoubleBookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private RentalBookingRepository rentalBookingRepository;
    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private PeachPaymentService peachPaymentService;

    private Listing tractor;
    private Provider provider;
    // A slot well in the future, on whole hours.
    private final Instant start = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    private final Instant end = start.plus(2, ChronoUnit.DAYS);

    @BeforeEach
    void seed() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        Category cat = fixtures.saveCategory("Machinery", "mach-" + u);
        provider = fixtures.saveActiveProvider("Hire Co", "hire-" + u);
        fixtures.saveActiveSubscription(provider);
        tractor = fixtures.saveRentListing(provider, cat, "Tractor", new BigDecimal("500"));
    }

    private ResultActions add(String session, Instant from, Instant to) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("listingId", tractor.getId());
        body.put("quantity", 3); // ignored for rentals
        body.put("rentalStart", from.toString());
        body.put("rentalEnd", to.toString());
        return mockMvc.perform(post("/api/public/cart/add")
                .header("X-Session-Id", session)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions checkout(String session) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guestName", "Test Guest");
        body.put("guestEmail", "guest+" + UUID.randomUUID() + "@rent.test");
        body.put("guestPhone", "0820000000");
        body.put("deliveryOrPickup", "PICKUP");
        body.put("paymentMethod", "CASH");
        return mockMvc.perform(post("/api/public/cart/checkout")
                .header("X-Session-Id", session)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private RentalBooking booking(BookingStatus status, Instant from, Instant to) {
        RentalBooking b = new RentalBooking();
        b.setProvider(provider);
        b.setListing(tractor);
        b.setGuestName("Earlier Guest");
        b.setGuestEmail("earlier@rent.test");
        b.setGuestPhone("0820000001");
        b.setDeliveryOrPickup("PICKUP");
        b.setStartAt(from);
        b.setEndAt(to);
        b.setTotalAmount(new BigDecimal("1000.00"));
        b.setStatus(status);
        b.setVerificationCode(UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return rentalBookingRepository.save(b);
    }

    @Test
    void aRentalIsAlwaysOneItemAndItsQuantityCannotChange() throws Exception {
        String s = "rent-" + UUID.randomUUID();
        String body = add(s, start, end)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(1))
                .andReturn().getResponse().getContentAsString();
        long lineId = objectMapper.readTree(body).at("/lines/0/lineId").asLong();

        mockMvc.perform(patch("/api/public/cart/items/{id}/quantity", lineId)
                        .header("X-Session-Id", s)
                        .contentType(APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RENTAL_QUANTITY"));
    }

    @Test
    void slotsHeldByUnpaidPaidOrOngoingBookingsCannotBeAdded() throws Exception {
        for (BookingStatus held : new BookingStatus[] {BookingStatus.PENDING_PAYMENT, BookingStatus.PAID, BookingStatus.COLLECTED}) {
            RentalBooking existing = booking(held, start, end);
            // Overlaps the end of the existing booking by one hour.
            add("rent-" + UUID.randomUUID(), end.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.DAYS))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RENTAL_CONFLICT"));
            existing.setStatus(BookingStatus.CANCELLED); // a cancelled booking frees the slot again
            rentalBookingRepository.save(existing);
        }
        // Back-to-back (starts exactly when the cancelled one ended) is fine, and cancelled ones never block.
        add("rent-" + UUID.randomUUID(), start, end).andExpect(status().isOk());
    }

    @Test
    void backToBackBookingsAreAllowed() throws Exception {
        booking(BookingStatus.PAID, start, end);
        add("rent-" + UUID.randomUUID(), end, end.plus(1, ChronoUnit.DAYS)).andExpect(status().isOk());
    }

    @Test
    void oneCartCannotHoldTheSameItemTwiceForOverlappingTimes() throws Exception {
        String s = "rent-" + UUID.randomUUID();
        add(s, start, end).andExpect(status().isOk());
        add(s, start, end)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENTAL_IN_CART"));
        add(s, start.plus(1, ChronoUnit.DAYS), end.plus(1, ChronoUnit.DAYS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENTAL_IN_CART"));
        // A different, non-overlapping time for the same item is fine.
        add(s, end, end.plus(1, ChronoUnit.DAYS)).andExpect(status().isOk());
    }

    @Test
    void rentalsCannotStartInThePastOrEndBeforeTheyStart() throws Exception {
        Instant past = Instant.now().minus(2, ChronoUnit.DAYS);
        add("rent-" + UUID.randomUUID(), past, past.plus(1, ChronoUnit.DAYS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RENTAL_DATES_INVALID"));
        add("rent-" + UUID.randomUUID(), end, start)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RENTAL_DATES_INVALID"));
    }

    @Test
    void secondBuyerWithTheSameSlotInTheirCartIsRefusedAtCheckout() throws Exception {
        // Both buyers add the same slot while it is still free.
        String first = "rent-a-" + UUID.randomUUID();
        String second = "rent-b-" + UUID.randomUUID();
        add(first, start, end).andExpect(status().isOk());
        add(second, start, end).andExpect(status().isOk());

        checkout(first).andExpect(status().isOk());
        // The first booking is then paid. Before this fix checkout only looked at unpaid bookings,
        // so the second buyer could still book the same slot.
        rentalBookingRepository.findOverlapping(tractor.getId(), java.util.Set.of(BookingStatus.PENDING_PAYMENT), start, end)
                .forEach(b -> {
                    b.setStatus(BookingStatus.PAID);
                    rentalBookingRepository.save(b);
                });

        checkout(second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RENTAL_CONFLICT"));
        assertThat(rentalBookingRepository.findOverlapping(
                        tractor.getId(), java.util.EnumSet.allOf(BookingStatus.class), start, end))
                .hasSize(1);
    }

    @Test
    void cancellingTheCardPaymentReleasesTheSlot() throws Exception {
        RentalBooking held = booking(BookingStatus.PENDING_PAYMENT, start, end);
        PaymentRecord pr = new PaymentRecord();
        pr.setProvider(provider);
        pr.setRentalBooking(held);
        pr.setMethod(PaymentMethod.PEACH);
        pr.setStatus(PaymentRecordStatus.PENDING_PAYMENT);
        pr.setAmount(held.getTotalAmount());
        pr.setGatewayMerchantRef("RentRef" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        pr.setGatewayCheckoutId("checkout-rent");
        paymentRecordRepository.save(pr);

        Map<String, String> callback = new LinkedHashMap<>();
        callback.put("merchantTransactionId", pr.getGatewayMerchantRef());
        callback.put("checkoutId", "checkout-rent");
        callback.put("amount", "1000.00");
        callback.put("currency", "ZAR");
        callback.put("paymentType", "DB");
        callback.put("authentication.entityId", "test-entity");
        callback.put("result.code", "100.396.101"); // cancelled by the buyer
        callback.put("signature", PeachSignatureUtil.buildSignature(callback, "webhook-signing-secret"));
        peachPaymentService.handleWebhook(callback);

        assertThat(rentalBookingRepository.findById(held.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        add("rent-" + UUID.randomUUID(), start, end).andExpect(status().isOk());
    }

    @Test
    void aRentalStartingTodayIsAllowed() throws Exception {
        // The marketplace books whole days: "today" is sent as today 00:00, which is already past.
        java.time.ZoneId za = java.time.ZoneId.of("Africa/Johannesburg");
        Instant todayStart = java.time.LocalDate.now(za).atStartOfDay(za).toInstant();
        add("rent-" + UUID.randomUUID(), todayStart, todayStart.plus(1, ChronoUnit.DAYS)).andExpect(status().isOk());
    }

    @Test
    void bookedSlotsArePublicWithoutPersonalDetails() throws Exception {
        booking(BookingStatus.PAID, start, end);
        booking(BookingStatus.CANCELLED, end, end.plus(1, ChronoUnit.DAYS)); // cancelled: not shown
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/public/listings/{id}/booked", tractor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].start").exists())
                .andExpect(jsonPath("$[0].end").exists())
                .andExpect(jsonPath("$[0].guestName").doesNotExist())
                .andExpect(jsonPath("$[0].guestEmail").doesNotExist());
    }
}
