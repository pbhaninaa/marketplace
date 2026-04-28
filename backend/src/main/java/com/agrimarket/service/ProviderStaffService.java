package com.agrimarket.service;

import com.agrimarket.api.dto.CreateStaffRequest;
import com.agrimarket.api.dto.PayrollEntryRequest;
import com.agrimarket.api.dto.PayrollEntryResponse;
import com.agrimarket.api.dto.StaffMemberResponse;
import com.agrimarket.api.dto.UpdateStaffRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.ProviderStaffPermission;
import com.agrimarket.domain.StaffPayrollEntry;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.ProviderStaffPermissionRepository;
import com.agrimarket.repo.StaffPayrollEntryRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderStaffService {

    private static final Set<UserRole> STAFF_ROLES =
            EnumSet.of(UserRole.PROVIDER_ADMIN, UserRole.PROVIDER_STAFF, UserRole.PROVIDER_VIEWER);

    private final UserAccountRepository userAccountRepository;
    private final ProviderRepository providerRepository;
    private final ProviderStaffPermissionRepository providerStaffPermissionRepository;
    private final ProviderPermissionService providerPermissionService;
    private final StaffPayrollEntryRepository staffPayrollEntryRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<StaffMemberResponse> listTeam(MarketUserPrincipal actor) {
        providerPermissionService.require(actor, ProviderPermissionKey.TEAM_READ);
        Provider p = loadProvider(actor.getProviderId());
        return userAccountRepository.findByProvider_IdOrderByEmailAsc(p.getId()).stream()
                .map(this::toStaffResponse)
                .toList();
    }

    @Transactional
    public StaffMemberResponse createStaff(MarketUserPrincipal actor, CreateStaffRequest req) {
        providerPermissionService.requireManageTeam(actor);
        if (!STAFF_ROLES.contains(req.role())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Only provider admin, staff, or viewer roles are allowed.");
        }
        Provider p = loadProvider(actor.getProviderId());
        if (userAccountRepository.findByEmailIgnoreCase(req.email()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already registered");
        }
        UserAccount u = new UserAccount(req.email(), passwordEncoder.encode(req.password()), req.role(), p);
        u.setStaffRateUnit(req.rateUnit());
        u.setStaffCompensationRate(req.rateAmount());
        userAccountRepository.save(u);
        setPermissions(actor, p, u, req.permissions());
        return toStaffResponse(u);
    }

    @Transactional
    public StaffMemberResponse updateStaff(MarketUserPrincipal actor, Long staffUserId, UpdateStaffRequest req) {
        providerPermissionService.requireManageTeam(actor);
        if (!STAFF_ROLES.contains(req.role())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Invalid role for staff member.");
        }
        UserAccount target = userAccountRepository
                .findByIdAndProvider_Id(staffUserId, actor.getProviderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STAFF", "User not found in this organization"));
        if (target.getRole() == UserRole.PROVIDER_OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_IMMUTABLE", "Provider owner cannot be edited here.");
        }
        if (target.getId().equals(actor.getUserId()) && req.role() != target.getRole()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_ROLE", "You cannot change your own role.");
        }
        target.setRole(req.role());
        target.setStaffRateUnit(req.rateUnit());
        target.setStaffCompensationRate(req.rateAmount());
        target.setEnabled(req.enabled());
        UserAccount saved = userAccountRepository.save(target);
        setPermissions(actor, loadProvider(actor.getProviderId()), saved, req.permissions());
        return toStaffResponse(saved);
    }

    @Transactional
    public void deleteStaff(MarketUserPrincipal actor, Long staffUserId) {
        providerPermissionService.requireManageTeam(actor);
        UserAccount target = userAccountRepository
                .findByIdAndProvider_Id(staffUserId, actor.getProviderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STAFF", "User not found in this organization"));
        if (target.getRole() == UserRole.PROVIDER_OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_IMMUTABLE", "Provider owner cannot be deleted.");
        }
        if (target.getId().equals(actor.getUserId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_DELETE", "You cannot delete your own account.");
        }
        // Soft delete: disable login + remove permissions; keep history for payroll/audit.
        target.setEnabled(false);
        userAccountRepository.save(target);
        if (target.getProvider() != null) {
            providerStaffPermissionRepository.deleteByProvider_IdAndUser_Id(target.getProvider().getId(), target.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<PayrollEntryResponse> listPayroll(MarketUserPrincipal actor, Long staffUserId) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_READ);
        loadProvider(actor.getProviderId());
        List<StaffPayrollEntry> rows =
                staffUserId == null
                        ? staffPayrollEntryRepository.findByProvider_IdOrderByCreatedAtDesc(actor.getProviderId())
                        : staffPayrollEntryRepository.findByProvider_IdAndStaff_IdOrderByCreatedAtDesc(
                                actor.getProviderId(), staffUserId);
        return rows.stream().map(this::toPayrollResponse).toList();
    }

    @Transactional
    public PayrollEntryResponse recordPayroll(MarketUserPrincipal actor, Long staffUserId, PayrollEntryRequest req) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_WRITE);
        UserAccount staff = userAccountRepository
                .findByIdAndProvider_Id(staffUserId, actor.getProviderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STAFF", "User not found in this organization"));
        if (staff.getRole() == UserRole.PROVIDER_OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_PAYROLL", "Payroll cannot be recorded for the owner via this flow.");
        }
        if (staff.getStaffCompensationRate() == null
                || staff.getStaffCompensationRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "NO_RATE",
                    "Staff member must have a compensation rate greater than zero.");
        }
        if (staff.getStaffRateUnit() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_RATE_UNIT", "Staff member must have a rate unit (hourly/daily/weekly).");
        }
        Provider p = loadProvider(actor.getProviderId());
        UserAccount recorder = userAccountRepository.findById(actor.getUserId()).orElse(null);

        BigDecimal amount = req.unitsWorked()
                .multiply(staff.getStaffCompensationRate())
                .setScale(2, RoundingMode.HALF_UP);

        StaffPayrollEntry e = new StaffPayrollEntry();
        e.setProvider(p);
        e.setStaff(staff);
        e.setUnitsWorked(req.unitsWorked());
        e.setRateSnapshot(staff.getStaffCompensationRate());
        e.setRateUnitSnapshot(staff.getStaffRateUnit());
        e.setAmount(amount);
        e.setNotes(req.notes());
        e.setRecordedBy(recorder);
        staffPayrollEntryRepository.save(e);
        return toPayrollResponse(e);
    }

    private Provider loadProvider(Long id) {
        return providerRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
    }

    private StaffMemberResponse toStaffResponse(UserAccount u) {
        boolean owner = u.getRole() == UserRole.PROVIDER_OWNER;
        EnumSet<ProviderPermissionKey> perms = EnumSet.noneOf(ProviderPermissionKey.class);
        if (!owner && u.getProvider() != null) {
            List<ProviderPermissionKey> keys = providerStaffPermissionRepository.findKeys(u.getProvider().getId(), u.getId());
            if (!keys.isEmpty()) {
                perms = EnumSet.copyOf(keys);
            }
        }
        return new StaffMemberResponse(
                u.getId(),
                u.getEmail(),
                u.getRole(),
                u.isEnabled(),
                owner,
                u.getStaffRateUnit(),
                u.getStaffCompensationRate(),
                perms);
    }

    private PayrollEntryResponse toPayrollResponse(StaffPayrollEntry e) {
        return new PayrollEntryResponse(
                e.getId(),
                e.getStaff().getId(),
                e.getStaff().getEmail(),
                e.getUnitsWorked(),
                e.getRateSnapshot(),
                e.getRateUnitSnapshot(),
                e.getAmount(),
                e.getNotes(),
                e.getCreatedAt().toString());
    }

    private void setPermissions(
            MarketUserPrincipal actor, Provider provider, UserAccount target, Set<ProviderPermissionKey> requested) {
        if (target.getRole() == UserRole.PROVIDER_OWNER) {
            return;
        }
        Set<ProviderPermissionKey> keys = requested == null ? Set.of() : requested;
        // Filter to keys applicable to this provider subtype
        Set<ProviderPermissionKey> applicable = providerPermissionService.applicableKeys(provider.getSubtype());
        EnumSet<ProviderPermissionKey> filtered = EnumSet.noneOf(ProviderPermissionKey.class);
        for (ProviderPermissionKey k : keys) {
            if (applicable.contains(k)) filtered.add(k);
        }
        if (!providerPermissionService.canGrant(actor, filtered)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PERMISSION_GRANT", "You cannot grant permissions you don't have.");
        }
        providerStaffPermissionRepository.deleteByProvider_IdAndUser_Id(provider.getId(), target.getId());
        for (ProviderPermissionKey k : filtered) {
            providerStaffPermissionRepository.save(new ProviderStaffPermission(provider, target, k));
        }
    }
}
