package com.agrimarket.api;

import com.agrimarket.api.dto.ReferralDtos.AdminReferralRow;
import com.agrimarket.api.dto.ReferralDtos.AdminStats;
import com.agrimarket.api.dto.ReferralDtos.MarkPaidRequest;
import com.agrimarket.api.dto.ReferralDtos.SettingsResponse;
import com.agrimarket.api.dto.ReferralDtos.SettingsUpdateRequest;
import com.agrimarket.domain.ReferralSettings;
import com.agrimarket.domain.ReferralStatus;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ReferralService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform-admin referral management: programme settings, referral list with filters, payouts. */
@RestController
@RequestMapping("/api/admin/referrals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminReferralsController {

    private final ReferralService referralService;

    @GetMapping("/settings")
    public SettingsResponse settings() {
        return toResponse(referralService.getSettings());
    }

    /** Only platform admins can change the commission rate and claim window. */
    @PutMapping("/settings")
    public SettingsResponse updateSettings(
            @AuthenticationPrincipal MarketUserPrincipal actor, @Valid @RequestBody SettingsUpdateRequest req) {
        return toResponse(referralService.updateSettings(
                req.commissionPercent(), req.claimWindowDays(), actor != null ? actor.getEmail() : null));
    }

    /**
     * Referrals filtered by who (referrer / referee: email, name, business or code), when (referral
     * date, inclusive {@code from}..{@code to} in UTC) and status.
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String referrer,
            @RequestParam(required = false) String referee,
            @RequestParam(required = false) ReferralStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AdminReferralRow> result = referralService.adminSearch(
                status,
                from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null,
                to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null,
                referrer,
                referee,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages());
    }

    @GetMapping("/stats")
    public AdminStats stats() {
        return referralService.adminStats();
    }

    /** Record a manual payout of earned commission (no automated payouts). */
    @PostMapping("/{id}/mark-paid")
    public AdminReferralRow markPaid(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) MarkPaidRequest req) {
        return ReferralService.toAdminRow(referralService.markPaid(
                id, req != null ? req.note() : null, actor != null ? actor.getEmail() : null));
    }

    private static SettingsResponse toResponse(ReferralSettings s) {
        return new SettingsResponse(
                s.getCommissionPercent(),
                s.getClaimWindowDays() != null ? s.getClaimWindowDays() : ReferralSettings.DEFAULT_CLAIM_WINDOW_DAYS,
                s.getUpdatedBy(),
                s.getUpdatedAt());
    }
}
