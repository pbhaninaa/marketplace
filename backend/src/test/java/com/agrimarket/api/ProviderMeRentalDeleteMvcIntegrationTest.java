package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.BookingStatus;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("ProviderMe rental delete integration")
class ProviderMeRentalDeleteMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestFixtures fixtures;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private ListingRepository listingRepository;
    @Autowired private RentalBookingRepository rentalBookingRepository;

    private String sessionId;
    private String providerToken;
    private Provider provider;
    private Long bookingId;
    private Listing rentListing;

    @BeforeEach
    void setup() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        sessionId = "rental-delete-session-" + u;

        Category cat = fixtures.saveCategory("Equipment", "equip-" + u);
        provider = fixtures.saveActiveProvider("Tools Co", "tools-" + u);
        fixtures.saveActiveSubscription(provider);

        rentListing = new Listing();
        rentListing.setProvider(provider);
        rentListing.setCategory(cat);
        rentListing.setListingType(ListingType.RENT);
        rentListing.setTitle("Tractor");
        rentListing.setDescription("Integration test rental listing");
        rentListing.setUnitPrice(BigDecimal.ZERO);
        rentListing.setRentPriceDaily(new BigDecimal("100"));
        rentListing.setActive(true);
        rentListing = listingRepository.save(rentListing);

        UserAccount owner = new UserAccount(
                "owner-" + u + "@test.com",
                passwordEncoder.encode("Password123!"),
                UserRole.PROVIDER_OWNER,
                provider);
        userAccountRepository.save(owner);
        providerToken = jwtService.createToken(owner.getId(), owner.getEmail(), owner.getRole(), provider.getId());
    }

    @Test
    @DisplayName("DELETE /api/provider/me/orders/rentals/{id} deletes cancelled booking")
    void deleteCancelledBooking() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(3, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/public/cart/add")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "listingId", rentListing.getId(),
                                "quantity", 1,
                                "rentalStart", start.toString(),
                                "rentalEnd", end.toString()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/public/cart/checkout")
                        .header("X-Session-Id", sessionId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "guestName", "Jane Doe",
                                "guestEmail", "jane@example.test",
                                "guestPhone", "0821234567",
                                "deliveryOrPickup", "Pickup",
                                "paymentMethod", "Cash"
                        ))))
                .andExpect(status().isOk());

        RentalBooking booking = rentalBookingRepository.findByProvider_IdOrderByCreatedAtDesc(
                        provider.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent()
                .stream()
                .findFirst()
                .orElseThrow();

        booking.setStatus(BookingStatus.CANCELLED);
        rentalBookingRepository.save(booking);
        bookingId = booking.getId();

        mockMvc.perform(delete("/api/provider/me/orders/rentals/" + bookingId)
                        .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());

        assertThat(rentalBookingRepository.findById(bookingId)).isEmpty();
    }
}

