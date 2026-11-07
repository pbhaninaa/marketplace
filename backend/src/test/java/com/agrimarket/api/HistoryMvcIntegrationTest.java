package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.PaymentRecordStatus;
import com.agrimarket.domain.PaymentStatus;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class HistoryMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private JwtService jwtService;

    private Provider providerA;
    private Provider providerB;
    private String tokenA;
    private String tokenB;
    private String adminToken;
    private String clientEmail;
    private Order a1;
    private Order a2;
    private Order b1;

    @BeforeEach
    void setUp() {
        String u = UUID.randomUUID().toString().substring(0, 8);
        providerA = fixtures.saveActiveProvider("Farm A", "farm-a-" + u);
        providerB = fixtures.saveActiveProvider("Farm B", "farm-b-" + u);
        fixtures.saveActiveSubscription(providerA);
        fixtures.saveActiveSubscription(providerB);
        tokenA = tokenFor(new UserAccount("a-" + u + "@test.com", "x", UserRole.PROVIDER_OWNER, providerA));
        tokenB = tokenFor(new UserAccount("b-" + u + "@test.com", "x", UserRole.PROVIDER_OWNER, providerB));
        adminToken = tokenFor(new UserAccount("admin-" + u + "@test.com", "x", UserRole.PLATFORM_ADMIN, null));
        clientEmail = "client-" + u + "@test.com";

        Instant now = Instant.now();
        a1 = order(providerA, clientEmail, OrderStatus.PAID, PaymentStatus.PAID, "100.00", now, PaymentMethod.CASH);
        a2 = order(providerA, "other-" + u + "@test.com", OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING, "40.00",
                now.minus(10, ChronoUnit.DAYS), PaymentMethod.EFT);
        b1 = order(providerB, clientEmail, OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING, "75.50", now, PaymentMethod.PEACH);
    }

    private String tokenFor(UserAccount user) {
        userAccountRepository.save(user);
        return "Bearer " + jwtService.createToken(
                user.getId(), user.getEmail(), user.getRole(), user.getProvider() != null ? user.getProvider().getId() : null);
    }

    private Order order(Provider p, String email, OrderStatus status, PaymentStatus ps, String total, Instant at, PaymentMethod method) {
        Order o = new Order();
        o.setProvider(p);
        o.setGuestName("Guest " + email.substring(0, 5));
        o.setGuestEmail(email);
        o.setGuestPhone("0820000000");
        o.setDeliveryOrPickup("Pickup");
        o.setStatus(status);
        o.setPaymentStatus(ps);
        o.setTotalAmount(new BigDecimal(total));
        o.setCreatedAt(at);
        o.setVerificationCode(UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        o = orderRepository.save(o);
        PaymentRecord pr = new PaymentRecord();
        pr.setProvider(p);
        pr.setOrder(o);
        pr.setMethod(method);
        pr.setStatus(ps == PaymentStatus.PAID ? PaymentRecordStatus.PAID : PaymentRecordStatus.PENDING_PAYMENT);
        pr.setAmount(new BigDecimal(total));
        pr.setCreatedAt(at);
        paymentRecordRepository.save(pr);
        return o;
    }

    @Test
    void providerSeesOnlyOwnOrdersEvenWhenAskingForAnotherProvider() throws Exception {
        mockMvc.perform(get("/api/provider/me/history/orders")
                        .header("Authorization", tokenA)
                        .param("providerId", String.valueOf(providerB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].providerId", everyItem(is(providerA.getId().intValue()))))
                // newest first; providers never see the meetup code
                .andExpect(jsonPath("$.content[0].id").value(a1.getId()))
                .andExpect(jsonPath("$.content[0].reference").value(nullValue()))
                .andExpect(jsonPath("$.summary.totalAmount").value(140.0))
                .andExpect(jsonPath("$.summary.paidAmount").value(100.0));

        mockMvc.perform(get("/api/provider/me/history/payments").header("Authorization", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value(b1.getId()))
                .andExpect(jsonPath("$.content[0].method").value("PEACH"));
    }

    @Test
    void orderFiltersStatusMethodDateAndText() throws Exception {
        mockMvc.perform(get("/api/provider/me/history/orders").header("Authorization", tokenA).param("status", "PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a2.getId()));
        mockMvc.perform(get("/api/provider/me/history/orders").header("Authorization", tokenA).param("method", "CASH"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("CASH"));
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        mockMvc.perform(get("/api/provider/me/history/orders").header("Authorization", tokenA)
                        .param("from", today).param("to", today))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a1.getId()));
        mockMvc.perform(get("/api/provider/me/history/orders").header("Authorization", tokenA).param("q", "other-"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a2.getId()));
        mockMvc.perform(get("/api/provider/me/history/orders").header("Authorization", tokenA).param("status", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serverSidePaginationReportsTotals() throws Exception {
        mockMvc.perform(get("/api/provider/me/history/orders").header("Authorization", tokenA).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(a2.getId()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void adminSeesAllProvidersAndCanFilterByProviderAndMethod() throws Exception {
        mockMvc.perform(get("/api/admin/history/orders").header("Authorization", adminToken).param("providerId", String.valueOf(providerB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].providerName").value("Farm B"));
        mockMvc.perform(get("/api/admin/history/payments").header("Authorization", adminToken)
                        .param("providerId", String.valueOf(providerA.getId())).param("paymentStatus", "PAID"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.summary.byMethod[0].method").value("CASH"));
        // Provider users are not admins
        mockMvc.perform(get("/api/admin/history/orders").header("Authorization", tokenA)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/provider/me/history/orders")).andExpect(status().is4xxClientError());
    }

    @Test
    void clientSeesOnlyOwnOrdersAcrossProvidersWithReference() throws Exception {
        String clientToken = "Bearer " + jwtService.createToken(99_999L, clientEmail.toUpperCase(), UserRole.CLIENT, null);
        mockMvc.perform(get("/api/public/client/history/orders").header("Authorization", clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].customerEmail", everyItem(is(clientEmail))))
                .andExpect(jsonPath("$.content[0].reference").isNotEmpty());
        mockMvc.perform(get("/api/public/client/history/payments").header("Authorization", clientToken).param("method", "PEACH"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value(b1.getId()));

        mockMvc.perform(get("/api/public/client/history/orders")).andExpect(status().isUnauthorized());
        // A provider token is not a client session
        mockMvc.perform(get("/api/public/client/history/orders").header("Authorization", tokenA)).andExpect(status().isForbidden());
    }

    @Test
    void exportsCsvAndExcelForTheFilteredRows() throws Exception {
        byte[] csv = mockMvc.perform(get("/api/provider/me/history/orders/export").header("Authorization", tokenA).param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).contains("Order #").contains(String.valueOf(a1.getId())).doesNotContain(clientEmail.replace("client", "other"));
        assertThat(text.lines().count()).isEqualTo(2);

        byte[] xlsx = mockMvc.perform(get("/api/admin/history/payments/export").header("Authorization", adminToken).param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andReturn().getResponse().getContentAsByteArray();
        boolean sawSheet = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    String sheet = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    assertThat(sheet).contains("Farm A").contains("Farm B").contains("75.50");
                    sawSheet = true;
                }
            }
        }
        assertThat(sawSheet).isTrue();
    }

    @Test
    void liveUpdatesStreamOpensForProvidersAndRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/me/live-updates").header("Authorization", tokenA).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"));
        mockMvc.perform(get("/api/me/live-updates").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().is4xxClientError());
    }
}
