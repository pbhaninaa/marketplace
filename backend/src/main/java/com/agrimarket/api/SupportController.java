package com.agrimarket.api;

import com.agrimarket.api.dto.ClientOtpRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.api.dto.SupportUserSearchResponse;
import com.agrimarket.domain.SupportTicket;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SupportTicketRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ClientOtpService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final ClientOtpService clientOtpService;
    private final UserAccountRepository userAccountRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final ProviderRepository providerRepository;
    private final JwtService jwtService;

    private void requireSupport(MarketUserPrincipal actor) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        if (actor.getRole() != UserRole.SUPPORT && actor.getRole() != UserRole.PLATFORM_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH", "Forbidden");
        }
    }

    @PostMapping("/client/otp/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendClientOtp(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @Valid @RequestBody ClientOtpRequest req) {
        requireSupport(actor);
        String target = req.target();
        if (target == null || target.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP", "Target is required");
        }
        clientOtpService.requestOtp(target);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@AuthenticationPrincipal MarketUserPrincipal actor) {
        requireSupport(actor);
        return Map.of(
                "tickets", supportTicketRepository.count(),
                "users", userAccountRepository.count(),
                "providers", providerRepository.count());
    }

    public record SupportTicketResponse(
            Long id,
            Long providerId,
            String subject,
            String description,
            String status,
            String createdAt) {
        public static SupportTicketResponse from(SupportTicket t) {
            return new SupportTicketResponse(
                    t.getId(),
                    t.getProvider() == null ? null : t.getProvider().getId(),
                    t.getSubject(),
                    t.getDescription(),
                    t.getStatus() == null ? null : t.getStatus().name(),
                    t.getCreatedAt() == null ? null : t.getCreatedAt().toString());
        }
    }

    @GetMapping("/tickets")
    public List<SupportTicketResponse> tickets(@AuthenticationPrincipal MarketUserPrincipal actor) {
        requireSupport(actor);
        return supportTicketRepository.findAll().stream().map(SupportTicketResponse::from).toList();
    }

    @GetMapping("/users")
    public List<SupportUserSearchResponse> users(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam(name = "q", required = false) String q) {
        requireSupport(actor);
        String needle = q == null ? "" : q.trim().toLowerCase();
        return userAccountRepository.findAll().stream()
                .filter(u -> needle.isBlank()
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(needle))
                        || (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(needle)))
                .limit(200)
                .map(u -> new SupportUserSearchResponse(
                        u.getId(),
                        u.getEmail(),
                        u.getDisplayName(),
                        u.getRole(),
                        u.isEnabled(),
                        u.getProvider() != null ? u.getProvider().getId() : null))
                .toList();
    }

    @PostMapping("/shadow/provider/{providerId}")
    public Map<String, Object> shadowProvider(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long providerId) {
        requireSupport(actor);
        var p = providerRepository.findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        String token = jwtService.createImpersonationToken(
                actor.getUserId(),
                actor.getEmail(),
                UserRole.PROVIDER_OWNER,
                p.getId());
        return Map.of(
                "token", token,
                "role", UserRole.PROVIDER_OWNER.name(),
                "providerId", p.getId(),
                "email", actor.getEmail(),
                "impersonated", true);
    }
}

