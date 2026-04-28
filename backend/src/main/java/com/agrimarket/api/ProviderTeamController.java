package com.agrimarket.api;

import com.agrimarket.api.dto.CreateStaffRequest;
import com.agrimarket.api.dto.PayrollEntryRequest;
import com.agrimarket.api.dto.PayrollEntryResponse;
import com.agrimarket.api.dto.StaffMemberResponse;
import com.agrimarket.api.dto.UpdateStaffRequest;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderStaffService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me")
@RequiredArgsConstructor
public class ProviderTeamController {

    private final ProviderStaffService providerStaffService;

    @GetMapping("/staff")
    public List<StaffMemberResponse> listStaff(@AuthenticationPrincipal MarketUserPrincipal actor) {
        return providerStaffService.listTeam(actor);
    }

    @PostMapping("/staff")
    public StaffMemberResponse inviteStaff(
            @AuthenticationPrincipal MarketUserPrincipal actor, @Valid @RequestBody CreateStaffRequest req) {
        return providerStaffService.createStaff(actor, req);
    }

    @PatchMapping("/staff/{userId}")
    public StaffMemberResponse updateStaff(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateStaffRequest req) {
        return providerStaffService.updateStaff(actor, userId, req);
    }

    @DeleteMapping("/staff/{userId}")
    public void removeStaff(@AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable Long userId) {
        providerStaffService.deleteStaff(actor, userId);
    }

    @GetMapping("/payroll-entries")
    public List<PayrollEntryResponse> listPayroll(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam(name = "staffUserId", required = false) Long staffUserId) {
        return providerStaffService.listPayroll(actor, staffUserId);
    }

    @PostMapping("/staff/{staffUserId}/payroll")
    public PayrollEntryResponse addPayroll(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long staffUserId,
            @Valid @RequestBody PayrollEntryRequest req) {
        return providerStaffService.recordPayroll(actor, staffUserId, req);
    }
}

