package com.agrimarket.api;

import com.agrimarket.api.dto.AdminProofDecisionRequest;
import com.agrimarket.api.dto.AdminProofRow;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.SubscriptionProofStatus;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.SubscriptionPaymentProofRepository;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.SubscriptionPaymentProofService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support/subscription-proofs")
@RequiredArgsConstructor
public class SupportSubscriptionProofsController {

    private final SubscriptionPaymentProofRepository proofRepository;
    private final SubscriptionPaymentProofService proofService;

    private void requireSupport(MarketUserPrincipal actor) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        if (actor.getRole() != UserRole.SUPPORT && actor.getRole() != UserRole.PLATFORM_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH", "Forbidden");
        }
    }

    @GetMapping("/pending")
    public List<AdminProofRow> pendingManual(@AuthenticationPrincipal MarketUserPrincipal actor) {
        requireSupport(actor);
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
    public ResponseEntity<byte[]> file(
            @AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable Long proofId) {
        requireSupport(actor);
        var proof = proofRepository
                .findById(proofId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROOF", "Proof not found"));
        String ct = proof.getContentType() != null ? proof.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .body(proof.getData());
    }

    @PostMapping("/{proofId}/decide")
    public void decide(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long proofId,
            @Valid @RequestBody AdminProofDecisionRequest req) {
        requireSupport(actor);
        proofService.decide(proofId, req);
    }
}

