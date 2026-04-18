package com.agrimarket.api;

import com.agrimarket.api.dto.CreateStaffRequest;
import com.agrimarket.api.dto.PayrollEntryRequest;
import com.agrimarket.api.dto.PayrollEntryResponse;
import com.agrimarket.api.dto.StaffMemberResponse;
import com.agrimarket.api.dto.UpdateStaffRequest;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderStaffService;
import com.agrimarket.service.TenantAccess;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/me")
@RequiredArgsConstructor
public class ProviderStaffController {

    private final ProviderStaffService providerStaffService;

    @GetMapping("/staff")
    public List<StaffMemberResponse> listStaff(@AuthenticationPrincipal MarketUserPrincipal user) {
        TenantAccess.requireProviderUser(user);
        return providerStaffService.listTeam(user);
    }

    @PostMapping("/staff")
    public StaffMemberResponse createStaff(
            @AuthenticationPrincipal MarketUserPrincipal user, @Valid @RequestBody CreateStaffRequest req) {
        TenantAccess.requireProviderUser(user);
        return providerStaffService.createStaff(user, req);
    }

    @PatchMapping("/staff/{id}")
    public StaffMemberResponse updateStaff(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStaffRequest req) {
        TenantAccess.requireProviderUser(user);
        return providerStaffService.updateStaff(user, id, req);
    }

    @DeleteMapping("/staff/{id}")
    public void deleteStaff(@AuthenticationPrincipal MarketUserPrincipal user, @PathVariable Long id) {
        TenantAccess.requireProviderUser(user);
        providerStaffService.deleteStaff(user, id);
    }

    /** Listed separately from `/staff/{id}` so `payroll-entries` is never parsed as a user id. */
    @GetMapping("/payroll-entries")
    public List<PayrollEntryResponse> listPayroll(
            @AuthenticationPrincipal MarketUserPrincipal user, @RequestParam(required = false) Long staffId) {
        TenantAccess.requireProviderUser(user);
        return providerStaffService.listPayroll(user, staffId);
    }

    @PostMapping("/staff/{staffId}/payroll")
    public PayrollEntryResponse recordPayroll(
            @AuthenticationPrincipal MarketUserPrincipal user,
            @PathVariable Long staffId,
            @Valid @RequestBody PayrollEntryRequest req) {
        TenantAccess.requireProviderUser(user);
        return providerStaffService.recordPayroll(user, staffId, req);
    }
}
