package com.agrimarket.api;

import com.agrimarket.api.dto.AdminProofDecisionRequest;
import com.agrimarket.api.dto.AdminProofRow;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.SubscriptionProofStatus;
import com.agrimarket.repo.SubscriptionPaymentProofRepository;
import com.agrimarket.service.SubscriptionPaymentProofService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/subscription-proofs")
@RequiredArgsConstructor
public class AdminSubscriptionProofsController {

    private final SubscriptionPaymentProofRepository proofRepository;
    private final SubscriptionPaymentProofService proofService;

    @GetMapping("/pending")
    public List<AdminProofRow> pending() {
        // Only items that failed auto-verification and require manual review.
        return proofRepository
                .findByStatusAndManualVerificationRequiredTrueOrderByCreatedAtAsc(SubscriptionProofStatus.PENDING)
                .stream()
                .map(p -> new AdminProofRow(
                        p.getId(),
                        p.getProvider().getId(),
                        p.getProvider().getName(),
                        p.getSubscription().getId(),
                        p.getSubscription().getPlan(),
                        p.getSubscription().getBillingCycle(),
                        p.getStatus(),
                        p.getCreatedAt()))
                .toList();
    }

    @GetMapping("/{proofId}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long proofId) {
        var proof = proofRepository
                .findById(proofId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROOF", "Proof not found"));
        String ct = proof.getContentType() != null ? proof.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .body(proof.getData());
    }

    @PostMapping("/{proofId}/decide")
    public void decide(@PathVariable Long proofId, @Valid @RequestBody AdminProofDecisionRequest req) {
        proofService.decide(proofId, req);
    }
}

