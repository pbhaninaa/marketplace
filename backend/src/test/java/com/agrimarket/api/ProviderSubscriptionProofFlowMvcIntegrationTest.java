package com.agrimarket.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrimarket.AbstractIntegrationTest;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.support.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class ProviderSubscriptionProofFlowMvcIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void bankDetails_withProviderJwt_returnsConfiguredDetails() throws Exception {
        Provider p = new Provider("Sub Provider", "sub-provider", "Test", "Cape Town");
        p.setStatus(ProviderStatus.ACTIVE);
        p = providerRepository.save(p);

        String email = "provider-sub-" + UUID.randomUUID() + "@integration.test";
        UserAccount u = new UserAccount(email, passwordEncoder.encode("irrelevant"), UserRole.PROVIDER_OWNER, p);
        userAccountRepository.save(u);
        userAccountRepository.flush();

        String token = jwtService.createToken(u.getId(), u.getEmail(), u.getRole(), p.getId());

        mockMvc.perform(get("/api/provider/me/subscription/bank-details")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("Test Bank"))
                .andExpect(jsonPath("$.accountName").value("Agri Marketplace"))
                .andExpect(jsonPath("$.accountNumber").value("1234567890"))
                .andExpect(jsonPath("$.branchCode").value("0001"))
                .andExpect(jsonPath("$.referenceHint").exists());
    }

    @Test
    void quote_uploadProof_autoApproves_makesSubscriptionActive() throws Exception {
        // Provider + provider user
        Provider p = fixtures.saveActiveProvider("Paid Provider", "paid-provider-" + UUID.randomUUID());
        String email = "provider-pay-" + UUID.randomUUID() + "@integration.test";
        UserAccount providerUser =
                new UserAccount(email, passwordEncoder.encode("irrelevant"), UserRole.PROVIDER_OWNER, p);
        userAccountRepository.save(providerUser);
        userAccountRepository.flush();
        String providerToken =
                jwtService.createToken(providerUser.getId(), providerUser.getEmail(), providerUser.getRole(), p.getId());

        // Quote a plan (no activation yet)
        MvcResult quoteRes = mockMvc.perform(get("/api/provider/me/subscription/quote")
                        .param("plan", "BASIC")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentId").exists())
                .andExpect(jsonPath("$.amountDue").value(199))
                .andExpect(jsonPath("$.paymentReference").exists())
                .andReturn();

        JsonNode quoteJson = objectMapper.readTree(quoteRes.getResponse().getContentAsString());
        long intentId = quoteJson.get("intentId").asLong();
        String expectedRef = quoteJson.get("paymentReference").asText();

        // Upload proof (PDF with embedded text matching auto-verification rules)
        MockMultipartFile proof = new MockMultipartFile("file", "proof.pdf", "application/pdf",
                buildTextPdfBytes("Payment ref: " + expectedRef + "\n"
                        + "Amount: 199.00\n"
                        + "Date: " + LocalDate.now() + "\n"));

        MockMultipartHttpServletRequestBuilder uploadReq =
                (MockMultipartHttpServletRequestBuilder) multipart("/api/provider/me/subscription/proof")
                        .param("intentId", String.valueOf(intentId));
        uploadReq.file(proof);

        MvcResult uploadRes = mockMvc.perform(uploadReq.header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proofId").exists())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadRes.getResponse().getContentAsString());
        long proofId = uploadJson.get("proofId").asLong();

        // Proof file can be fetched by admin
        String adminEmail = "admin-proof-" + UUID.randomUUID() + "@integration.test";
        UserAccount admin =
                new UserAccount(adminEmail, passwordEncoder.encode("irrelevant"), UserRole.PLATFORM_ADMIN, null);
        userAccountRepository.save(admin);
        userAccountRepository.flush();
        String adminToken = jwtService.createToken(admin.getId(), admin.getEmail(), admin.getRole(), null);

        mockMvc.perform(get("/api/admin/subscription-proofs/" + proofId + "/file")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String ct = result.getResponse().getContentType();
                    if (ct == null || !(ct.startsWith("image/") || ct.equalsIgnoreCase("application/pdf"))) {
                        throw new AssertionError("Expected image/pdf content type but got: " + ct);
                    }
                });

        // Provider status now active + valid true
        mockMvc.perform(get("/api/provider/me/subscription/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void uploadProof_withMatchingDateAmountAndReference_autoApproves() throws Exception {
        Provider p = fixtures.saveActiveProvider("Auto Provider", "auto-provider-" + UUID.randomUUID());
        String email = "provider-auto-" + UUID.randomUUID() + "@integration.test";
        UserAccount providerUser =
                new UserAccount(email, passwordEncoder.encode("irrelevant"), UserRole.PROVIDER_OWNER, p);
        userAccountRepository.save(providerUser);
        userAccountRepository.flush();
        String providerToken =
                jwtService.createToken(providerUser.getId(), providerUser.getEmail(), providerUser.getRole(), p.getId());

        // Quote a plan
        MvcResult quoteRes = mockMvc.perform(get("/api/provider/me/subscription/quote")
                        .param("plan", "BASIC")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentId").exists())
                .andExpect(jsonPath("$.paymentReference").exists())
                .andReturn();

        JsonNode quoteJson = objectMapper.readTree(quoteRes.getResponse().getContentAsString());
        long intentId = quoteJson.get("intentId").asLong();

        MockMultipartHttpServletRequestBuilder autoReq =
                (MockMultipartHttpServletRequestBuilder) multipart("/api/provider/me/subscription/proof")
                        .param("intentId", String.valueOf(intentId));
        String expectedRef = quoteJson.get("paymentReference").asText();
        String expectedAmount = quoteJson.get("amountDue").asText();
        MockMultipartFile proof = new MockMultipartFile("file", "proof.pdf", "application/pdf",
                buildTextPdfBytes("Ref: " + expectedRef + "\n"
                        + "Amount: " + expectedAmount + ".00\n"
                        + "Date: " + LocalDate.now() + "\n"));
        autoReq.file(proof);

        mockMvc.perform(autoReq.header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/provider/me/subscription/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    private static byte[] buildTextPdfBytes(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                for (String line : text.split("\\n")) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -16);
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}

