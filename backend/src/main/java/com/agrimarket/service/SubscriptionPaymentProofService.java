package com.agrimarket.service;

import com.agrimarket.api.dto.AdminProofDecisionRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionActivationIntent;
import com.agrimarket.domain.SubscriptionPaymentProof;
import com.agrimarket.domain.SubscriptionProofStatus;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SubscriptionPaymentProofRepository;
import com.agrimarket.repo.SubscriptionRepository;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@Service
@RequiredArgsConstructor
public class SubscriptionPaymentProofService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentProofRepository proofRepository;
    private final ProviderRepository providerRepository;
    private final SubscriptionService subscriptionService;
    private final SubscriptionQuoteService quoteService;

    @Transactional
    public SubscriptionPaymentProof upload(
            Long providerId, Long intentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD", "Proof of payment file is required");
        }
        if (intentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION", "Payment quote is required");
        }

        SubscriptionActivationIntent intent = quoteService.requireIntent(providerId, intentId);
        if (intent.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION", "This payment quote was already used");
        }

        String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        boolean ok = ct.startsWith("image/") || ct.equalsIgnoreCase("application/pdf");
        if (!ok) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD", "Upload an image or PDF proof of payment");
        }

        var provider = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));

        try {
            // Create a subscription row only when proof is submitted.
            Subscription sub = new Subscription();
            sub.setProvider(provider);
            sub.setPlan(intent.getPlan());
            sub.setBillingCycle(BillingCycle.MONTHLY);
            sub.setStatus(SubscriptionStatus.PENDING_VERIFICATION);
            Instant now = Instant.now();
            // DB column is non-null; treat this as the "current cycle end" even before approval.
            sub.setExpiresAt(now.plus(30, ChronoUnit.DAYS));
            sub.setCreatedAt(now);
            sub.setAmountDue(intent.getAmountDue());
            sub.setPaymentReference(intent.getPaymentReference());
            sub = subscriptionRepository.save(sub);

            byte[] bytes = file.getBytes();

            SubscriptionPaymentProof proof = new SubscriptionPaymentProof();
            proof.setProvider(provider);
            proof.setSubscription(sub);
            proof.setOriginalFilename(file.getOriginalFilename());
            proof.setContentType(ct);
            proof.setData(bytes);
            proof.setStatus(SubscriptionProofStatus.PENDING);

            ExtractedPaymentDetails extracted = extractPaymentDetails(bytes, ct, sub.getAmountDue(), sub.getPaymentReference());
            proof.setPaymentDate(extracted.date());
            proof.setPaymentAmount(extracted.amount());
            proof.setPaymentReference(extracted.reference());
            proof.setCreatedAt(Instant.now());
            SubscriptionPaymentProof saved = proofRepository.save(proof);

            // Auto verification: date == today, amount matches, reference matches.
            List<String> failures = new ArrayList<>();
            LocalDate today = LocalDate.now();
            if (extracted.date() == null || !extracted.date().equals(today)) {
                failures.add("Payment date must be today's date");
            }
            if (extracted.amount() == null || sub.getAmountDue() == null || extracted.amount().compareTo(sub.getAmountDue()) != 0) {
                failures.add("Payment amount does not match the selected plan amount");
            }
            String expectedRef = sub.getPaymentReference() == null ? "" : sub.getPaymentReference().trim();
            String actualRef = extracted.reference() == null ? "" : extracted.reference().trim();
            if (expectedRef.isEmpty() || !expectedRef.equalsIgnoreCase(actualRef)) {
                failures.add("Payment reference does not match the auto-generated reference");
            }
            failures.addAll(extracted.extractionFailures());

            if (failures.isEmpty()) {
                // Auto approve
                saved.setStatus(SubscriptionProofStatus.APPROVED);
                saved.setReviewedAt(Instant.now());
                saved.setReviewNote("Auto-verified");
                proofRepository.save(saved);
                subscriptionService.approveSubscription(sub);
            } else {
                // Route to manual verification (Support)
                saved.setManualVerificationRequired(true);
                saved.setReviewNote("Auto-verification failed: " + String.join("; ", failures));
                proofRepository.save(saved);
            }
            // Mark intent used (one-time reference).
            intent.setUsed(true);
            return saved;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD", "Failed to save proof");
        }
    }

    private record ExtractedPaymentDetails(LocalDate date, BigDecimal amount, String reference, List<String> extractionFailures) {}

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(R\\s*)?(\\d{1,3}(?:[ ,]\\d{3})*(?:[\\.,]\\d{2})?)");
    private static final Pattern DATE_YYYY_MM_DD = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern DATE_DD_MM_YYYY = Pattern.compile("\\b(\\d{2})[\\./-](\\d{2})[\\./-](\\d{4})\\b");

    private static ExtractedPaymentDetails extractPaymentDetails(byte[] bytes, String contentType, BigDecimal expectedAmount, String expectedReference) {
        List<String> failures = new ArrayList<>();
        String ref = expectedReference == null ? "" : expectedReference.trim();

        String text = "";
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            text = extractPdfText(bytes);
        } else if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            failures.add("Could not extract payment details from image proof (OCR not configured)");
        } else {
            failures.add("Unsupported proof content type for auto-verification");
        }

        String normalized = text == null ? "" : text.replace("\u00A0", " ").trim();
        if (normalized.isBlank()) {
            failures.add("Could not read text from proof for auto-verification");
        }

        // Reference: require the exact expected reference to appear in the proof text.
        String foundRef = null;
        if (!ref.isBlank() && normalized.toLowerCase(Locale.ROOT).contains(ref.toLowerCase(Locale.ROOT))) {
            foundRef = ref;
        } else {
            failures.add("Could not find the expected payment reference in the proof");
        }

        // Date: accept yyyy-mm-dd or dd/mm/yyyy variants; pick first match.
        LocalDate foundDate = null;
        var m1 = DATE_YYYY_MM_DD.matcher(normalized);
        if (m1.find()) {
            try {
                foundDate = LocalDate.of(Integer.parseInt(m1.group(1)), Integer.parseInt(m1.group(2)), Integer.parseInt(m1.group(3)));
            } catch (Exception ignored) {}
        } else {
            var m2 = DATE_DD_MM_YYYY.matcher(normalized);
            if (m2.find()) {
                try {
                    foundDate = LocalDate.of(Integer.parseInt(m2.group(3)), Integer.parseInt(m2.group(2)), Integer.parseInt(m2.group(1)));
                } catch (Exception ignored) {}
            }
        }
        if (foundDate == null) {
            failures.add("Could not find a payment date in the proof");
        }

        // Amount: find a value that matches expectedAmount exactly to cents.
        BigDecimal foundAmount = null;
        if (expectedAmount != null && !normalized.isBlank()) {
            var matcher = AMOUNT_PATTERN.matcher(normalized);
            while (matcher.find()) {
                String raw = matcher.group(2);
                if (raw == null) continue;
                String cleaned = raw.replace(" ", "").replace(",", "");
                // If decimal is comma, normalize to dot.
                if (cleaned.chars().filter(ch -> ch == '.').count() == 0 && cleaned.chars().filter(ch -> ch == ',').count() == 1) {
                    cleaned = cleaned.replace(",", ".");
                }
                try {
                    BigDecimal candidate = new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal exp = expectedAmount.setScale(2, RoundingMode.HALF_UP);
                    if (candidate.compareTo(exp) == 0) {
                        foundAmount = candidate;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (foundAmount == null) {
            failures.add("Could not find the expected payment amount in the proof");
        }

        return new ExtractedPaymentDetails(foundDate, foundAmount, foundRef, failures);
    }

    private static String extractPdfText(byte[] bytes) {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            return "";
        }
    }

    @Transactional
    public void decide(Long proofId, AdminProofDecisionRequest req) {
        SubscriptionPaymentProof proof = proofRepository
                .findById(proofId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROOF", "Proof not found"));

        if (proof.getStatus() != SubscriptionProofStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROOF", "Proof was already reviewed");
        }

        boolean approve = Boolean.TRUE.equals(req.approve());
        proof.setReviewedAt(Instant.now());
        proof.setReviewNote(req.note());
        proof.setStatus(approve ? SubscriptionProofStatus.APPROVED : SubscriptionProofStatus.REJECTED);

        Subscription sub = proof.getSubscription();
        if (approve) {
            subscriptionService.approveSubscription(sub);
        } else {
            sub.setStatus(SubscriptionStatus.REJECTED);
            subscriptionRepository.save(sub);
        }
        proofRepository.save(proof);
    }
}

