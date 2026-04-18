package com.agrimarket.api;

import com.agrimarket.api.dto.ClientOtpRequest;
import com.agrimarket.api.dto.SupportUserProfileResponse;
import com.agrimarket.api.dto.SupportUserSearchResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.TicketStatus;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.PurchaseOrderRepository;
import com.agrimarket.repo.SupportTicketRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.service.ClientOtpService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final UserAccountRepository userAccountRepository;
    private final ProviderRepository providerRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final ClientOtpService clientOtpService;

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return Map.of(
                "userCount", userAccountRepository.count(),
                "providerCount", providerRepository.count(),
                "purchaseOrderCount", purchaseOrderRepository.count(),
                "openTickets",
                supportTicketRepository.findAll().stream()
                        .filter(t -> t.getStatus() == TicketStatus.OPEN
                                || t.getStatus() == TicketStatus.IN_PROGRESS)
                        .count());
    }

    @GetMapping("/tickets")
    public Object tickets() {
        return supportTicketRepository.findAll(PageRequest.of(0, 100)).getContent();
    }

    @GetMapping("/users")
    public List<SupportUserSearchResponse> searchUsers(String q) {
        String term = q == null ? "" : q.trim().toLowerCase();
        if (term.isEmpty()) {
            return userAccountRepository.findAll(PageRequest.of(0, 50, Sort.by("id").descending()))
                    .getContent()
                    .stream()
                    .map(this::toSearchResponse)
                    .toList();
        }
        return userAccountRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().toLowerCase().contains(term))
                .limit(50)
                .map(this::toSearchResponse)
                .toList();
    }

    @GetMapping("/users/{id}")
    public SupportUserProfileResponse user(@PathVariable Long id) {
        UserAccount u = userAccountRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER", "User not found"));
        return new SupportUserProfileResponse(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.isEnabled(),
                u.getProvider() != null ? u.getProvider().getId() : null,
                u.getProvider() != null ? u.getProvider().getName() : null,
                u.getProvider() != null ? u.getProvider().getLocation() : null
        );
    }

    @PostMapping("/client/otp/resend")
    public void resendClientOtp(@RequestBody ClientOtpRequest req) {
        clientOtpService.requestOtp(req.target());
    }

    private SupportUserSearchResponse toSearchResponse(UserAccount u) {
        return new SupportUserSearchResponse(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.isEnabled(),
                u.getProvider() != null ? u.getProvider().getId() : null
        );
    }
}
