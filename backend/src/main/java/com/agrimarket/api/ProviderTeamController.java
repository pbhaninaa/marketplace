package com.agrimarket.api;

import com.agrimarket.api.dto.CreateStaffRequest;
import com.agrimarket.api.dto.MarkStaffOrderPayrollRequest;
import com.agrimarket.api.dto.StaffIncomeBundleDto;
import com.agrimarket.api.dto.StaffMemberResponse;
import com.agrimarket.api.dto.StaffPaymentCalculationsBundleDto;
import com.agrimarket.api.dto.UpdateStaffRequest;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderStaffService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

    /** Order-attributed payroll summary (Wheel Hub payment-calculations). */
    @GetMapping("/staff/payment-calculations")
    public StaffPaymentCalculationsBundleDto paymentCalculations(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return providerStaffService.listPaymentCalculations(actor, startDate, endDate);
    }

    /** Staff self-view: expected salary from attributed collected orders. */
    @GetMapping("/staff/my-expected-income")
    public StaffIncomeBundleDto myExpectedIncome(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return providerStaffService.myExpectedIncome(actor, startDate, endDate);
    }

    /** Per-order staff income lines for employer payout UI. */
    @GetMapping("/staff/{staffUserId}/income")
    public StaffIncomeBundleDto staffIncome(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long staffUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return providerStaffService.staffIncome(actor, staffUserId, startDate, endDate);
    }

    @PostMapping("/staff/{staffUserId}/payroll-marks")
    public Map<String, String> markPayrollPaid(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long staffUserId,
            @Valid @RequestBody MarkStaffOrderPayrollRequest body) {
        providerStaffService.markOrderPayrollPaid(actor, staffUserId, body);
        return Map.of("status", "marked");
    }

    @DeleteMapping("/staff/{staffUserId}/payroll-marks")
    public Map<String, String> unmarkPayrollPaid(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long staffUserId,
            @RequestParam Long orderId) {
        providerStaffService.unmarkOrderPayrollPaid(actor, staffUserId, orderId);
        return Map.of("status", "cleared");
    }

    @PostMapping("/staff/{staffUserId}/payroll-marks/pay-all")
    public Map<String, Object> payAllUnpaid(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @PathVariable Long staffUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Boolean includeBonus) {
        int count = providerStaffService.markAllUnpaidPayrollPaid(
                actor, staffUserId, startDate, endDate, includeBonus);
        return Map.of("markedCount", count);
    }
}
