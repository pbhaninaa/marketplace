package com.agrimarket.service;

import com.agrimarket.api.dto.CreateStaffRequest;
import com.agrimarket.api.dto.MarkStaffOrderPayrollRequest;
import com.agrimarket.api.dto.PayrollEntryRequest;
import com.agrimarket.api.dto.PayrollEntryResponse;
import com.agrimarket.api.dto.StaffIncomeBundleDto;
import com.agrimarket.api.dto.StaffIncomeLineDto;
import com.agrimarket.api.dto.StaffMemberResponse;
import com.agrimarket.api.dto.StaffPaymentCalculationDto;
import com.agrimarket.api.dto.StaffPaymentCalculationsBundleDto;
import com.agrimarket.api.dto.UpdateStaffRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.ProviderStaffPermission;
import com.agrimarket.domain.StaffPayrollEntry;
import com.agrimarket.domain.StaffPayrollJobMark;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.ProviderStaffPermissionRepository;
import com.agrimarket.repo.StaffPayrollEntryRepository;
import com.agrimarket.repo.StaffPayrollJobMarkRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderStaffService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final Set<UserRole> STAFF_ROLES =
            EnumSet.of(UserRole.PROVIDER_ADMIN, UserRole.PROVIDER_STAFF, UserRole.PROVIDER_VIEWER);

    private final UserAccountRepository userAccountRepository;
    private final ProviderRepository providerRepository;
    private final ProviderStaffPermissionRepository providerStaffPermissionRepository;
    private final ProviderPermissionService providerPermissionService;
    private final StaffPayrollEntryRepository staffPayrollEntryRepository;
    private final StaffPayrollJobMarkRepository staffPayrollJobMarkRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    /** Defaults when owner creates staff and leaves permissions empty (Wheel Hub style). */
    public static EnumSet<ProviderPermissionKey> basePermissionsForNewStaff(Provider provider) {
        EnumSet<ProviderPermissionKey> keys = EnumSet.noneOf(ProviderPermissionKey.class);
        keys.add(ProviderPermissionKey.LISTINGS_READ);
        keys.add(ProviderPermissionKey.LISTINGS_WRITE);
        keys.add(ProviderPermissionKey.TEAM_READ);
        if (provider.getSubtype() != null) {
            switch (provider.getSubtype()) {
                case RESELLER -> {
                    keys.add(ProviderPermissionKey.ORDERS_READ);
                    keys.add(ProviderPermissionKey.ORDERS_WRITE);
                }
                case RENTING_OWNER -> {
                    keys.add(ProviderPermissionKey.RENTALS_READ);
                    keys.add(ProviderPermissionKey.RENTALS_WRITE);
                }
            }
        }
        return keys;
    }

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
        applyProfileFields(u, req.firstName(), req.lastName(), req.phoneNumber());
        u.setStaffRateUnit(req.rateUnit());
        u.setStaffCompensationRate(req.rateAmount());
        u.setStaffTargetPeriod(normalizeTargetPeriod(req.targetPeriod()));
        u.setStaffTargetValue(req.targetValue());
        u.setStaffBonusPercentage(req.bonusPercentage());
        userAccountRepository.save(u);

        // Staff with TEAM_MANAGE: copy creator permissions exactly (Wheel Hub rule).
        UserAccount actorAccount = userAccountRepository.findById(actor.getUserId()).orElse(null);
        boolean actorIsOwner = actorAccount != null && actorAccount.getRole() == UserRole.PROVIDER_OWNER;
        if (!actorIsOwner) {
            List<ProviderPermissionKey> mine =
                    providerStaffPermissionRepository.findKeys(p.getId(), actor.getUserId());
            setPermissions(actor, p, u, new HashSet<>(mine));
        } else {
            Set<ProviderPermissionKey> requested = req.permissions();
            if (requested == null || requested.isEmpty()) {
                requested = basePermissionsForNewStaff(p);
            }
            setPermissions(actor, p, u, requested);
        }
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
        applyProfileFields(target, req.firstName(), req.lastName(), req.phoneNumber());
        target.setStaffRateUnit(req.rateUnit());
        target.setStaffCompensationRate(req.rateAmount());
        target.setStaffTargetPeriod(normalizeTargetPeriod(req.targetPeriod()));
        target.setStaffTargetValue(req.targetValue());
        target.setStaffBonusPercentage(req.bonusPercentage());
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
        UserAccount staff = requireStaff(actor.getProviderId(), staffUserId);
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_RATE_UNIT", "Staff member must have a rate unit.");
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

    @Transactional(readOnly = true)
    public StaffPaymentCalculationsBundleDto listPaymentCalculations(
            MarketUserPrincipal actor, LocalDate startDate, LocalDate endDate) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_READ);
        Provider p = loadProvider(actor.getProviderId());
        List<Order> completed = completedOrdersForProvider(p.getId(), startDate, endDate);
        List<StaffPaymentCalculationDto> rows = new ArrayList<>();
        for (UserAccount staff : userAccountRepository.findByProvider_IdOrderByEmailAsc(p.getId())) {
            if (staff.getRole() == UserRole.PROVIDER_OWNER || !staff.isEnabled()) {
                continue;
            }
            rows.add(calculateForStaff(staff, completed));
        }
        return new StaffPaymentCalculationsBundleDto(startDate, endDate, rows);
    }

    @Transactional(readOnly = true)
    public StaffIncomeBundleDto staffIncome(
            MarketUserPrincipal actor, Long staffUserId, LocalDate startDate, LocalDate endDate) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_READ);
        UserAccount staff = requireStaff(actor.getProviderId(), staffUserId);
        if (staff.getRole() == UserRole.PROVIDER_OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_PAYROLL", "Owner has no staff income lines.");
        }
        List<Order> completed = completedOrdersForProvider(actor.getProviderId(), startDate, endDate).stream()
                .filter(o -> staff.getId().equals(o.getCompletedByStaffId()))
                .toList();
        StaffPaymentCalculationDto calc = calculateForStaff(staff, completedOrdersForProvider(actor.getProviderId(), startDate, endDate));
        Set<Long> paidOrderIds = new HashSet<>();
        if (!completed.isEmpty()) {
            paidOrderIds.addAll(staffPayrollJobMarkRepository
                    .findByStaff_IdAndOrder_IdIn(
                            staff.getId(), completed.stream().map(Order::getId).toList())
                    .stream()
                    .map(m -> m.getOrder().getId())
                    .collect(Collectors.toSet()));
        }

        StaffRateUnit method = staff.getStaffRateUnit();
        BigDecimal rate = staff.getStaffCompensationRate() == null ? BigDecimal.ZERO : staff.getStaffCompensationRate();
        List<StaffIncomeLineDto> lines = new ArrayList<>();
        for (Order o : completed) {
            BigDecimal units = unitsForSingleOrder(method, o, completed);
            BigDecimal lineAmount = lineAmount(method, rate, units, o);
            lines.add(new StaffIncomeLineDto(
                    o.getId(),
                    o.getGuestName(),
                    o.getTotalAmount(),
                    o.getCompletedAt() != null ? o.getCompletedAt() : o.getCreatedAt(),
                    units,
                    lineAmount,
                    paidOrderIds.contains(o.getId())));
        }
        return new StaffIncomeBundleDto(
                staff.getId(),
                staff.getEmail(),
                method,
                rate,
                startDate,
                endDate,
                calc.expectedPayment(),
                calc.paidPayment(),
                calc.unpaidPayment(),
                lines);
    }

    @Transactional
    public void markOrderPayrollPaid(MarketUserPrincipal actor, Long staffUserId, MarkStaffOrderPayrollRequest req) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_WRITE);
        UserAccount staff = requireStaff(actor.getProviderId(), staffUserId);
        Order order = orderRepository
                .findById(req.orderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER", "Order not found"));
        if (!order.getProvider().getId().equals(actor.getProviderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORDER", "Not your order");
        }
        if (order.getStatus() != OrderStatus.COLLECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER", "Only collected orders can be marked paid for payroll");
        }
        if (!staff.getId().equals(order.getCompletedByStaffId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER", "Order is not attributed to this staff member");
        }
        if (staffPayrollJobMarkRepository.existsByStaff_IdAndOrder_Id(staff.getId(), order.getId())) {
            return;
        }
        StaffPayrollJobMark mark = new StaffPayrollJobMark();
        mark.setProvider(loadProvider(actor.getProviderId()));
        mark.setStaff(staff);
        mark.setOrder(order);
        mark.setMarkedBy(userAccountRepository.findById(actor.getUserId()).orElse(null));
        mark.setMarkedAt(Instant.now());
        staffPayrollJobMarkRepository.save(mark);
    }

    @Transactional
    public void unmarkOrderPayrollPaid(MarketUserPrincipal actor, Long staffUserId, Long orderId) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_WRITE);
        requireStaff(actor.getProviderId(), staffUserId);
        staffPayrollJobMarkRepository.deleteByStaff_IdAndOrder_Id(staffUserId, orderId);
    }

    @Transactional
    public int markAllUnpaidPayrollPaid(MarketUserPrincipal actor, Long staffUserId, LocalDate startDate, LocalDate endDate) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_WRITE);
        UserAccount staff = requireStaff(actor.getProviderId(), staffUserId);
        List<Order> completed = completedOrdersForProvider(actor.getProviderId(), startDate, endDate).stream()
                .filter(o -> staff.getId().equals(o.getCompletedByStaffId()))
                .toList();
        Set<Long> paid = new HashSet<>();
        if (!completed.isEmpty()) {
            paid.addAll(staffPayrollJobMarkRepository
                    .findByStaff_IdAndOrder_IdIn(
                            staff.getId(), completed.stream().map(Order::getId).toList())
                    .stream()
                    .map(m -> m.getOrder().getId())
                    .collect(Collectors.toSet()));
        }
        Provider p = loadProvider(actor.getProviderId());
        UserAccount marker = userAccountRepository.findById(actor.getUserId()).orElse(null);
        int count = 0;
        for (Order o : completed) {
            if (paid.contains(o.getId())) {
                continue;
            }
            StaffPayrollJobMark mark = new StaffPayrollJobMark();
            mark.setProvider(p);
            mark.setStaff(staff);
            mark.setOrder(o);
            mark.setMarkedBy(marker);
            mark.setMarkedAt(Instant.now());
            staffPayrollJobMarkRepository.save(mark);
            count++;
        }
        return count;
    }

    private StaffPaymentCalculationDto calculateForStaff(UserAccount staff, List<Order> allCompleted) {
        List<Order> mine = allCompleted.stream()
                .filter(o -> staff.getId().equals(o.getCompletedByStaffId()))
                .toList();
        StaffRateUnit method = staff.getStaffRateUnit();
        BigDecimal rate = staff.getStaffCompensationRate() == null ? BigDecimal.ZERO : staff.getStaffCompensationRate();
        long jobCount = mine.size();
        BigDecimal units = BigDecimal.ZERO;
        BigDecimal expected = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (method != null && rate.compareTo(BigDecimal.ZERO) > 0) {
            switch (method) {
                case PER_SERVICE -> {
                    units = BigDecimal.valueOf(jobCount);
                    expected = units.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                }
                case HOURLY -> {
                    // 1 hour per completed order when duration is unknown
                    units = BigDecimal.valueOf(jobCount);
                    expected = units.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                }
                case DAILY -> {
                    Set<LocalDate> days = mine.stream()
                            .map(this::completionDate)
                            .collect(Collectors.toSet());
                    units = BigDecimal.valueOf(days.size());
                    expected = units.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                }
                case WEEKLY -> {
                    WeekFields wf = WeekFields.of(Locale.getDefault());
                    Set<String> weeks = mine.stream()
                            .map(o -> {
                                LocalDate d = completionDate(o);
                                return d.get(wf.weekBasedYear()) + "-W" + d.get(wf.weekOfWeekBasedYear());
                            })
                            .collect(Collectors.toSet());
                    units = BigDecimal.valueOf(weeks.size());
                    expected = units.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                }
                case MONTHLY -> {
                    units = BigDecimal.ZERO;
                    expected = rate.setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        // Bonus on expected
        if (staff.getStaffBonusPercentage() != null
                && staff.getStaffBonusPercentage().compareTo(BigDecimal.ZERO) > 0
                && expected.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal bonus = expected
                    .multiply(staff.getStaffBonusPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            expected = expected.add(bonus);
        }

        Set<Long> paidIds = new HashSet<>();
        if (!mine.isEmpty()) {
            paidIds.addAll(staffPayrollJobMarkRepository
                    .findByStaff_IdAndOrder_IdIn(staff.getId(), mine.stream().map(Order::getId).toList())
                    .stream()
                    .map(m -> m.getOrder().getId())
                    .collect(Collectors.toSet()));
        }

        BigDecimal paid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (method == StaffRateUnit.MONTHLY) {
            // Monthly: treat as fully paid only when all attributed orders in period are marked (or none exist)
            if (!mine.isEmpty() && paidIds.size() >= mine.size()) {
                paid = expected;
            } else if (mine.isEmpty()) {
                paid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            for (Order o : mine) {
                if (!paidIds.contains(o.getId())) {
                    continue;
                }
                BigDecimal u = unitsForSingleOrder(method, o, mine);
                paid = paid.add(lineAmount(method, rate, u, o));
            }
            if (staff.getStaffBonusPercentage() != null
                    && staff.getStaffBonusPercentage().compareTo(BigDecimal.ZERO) > 0
                    && paid.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal bonus = paid.multiply(staff.getStaffBonusPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                paid = paid.add(bonus);
            }
        }
        BigDecimal unpaid = expected.subtract(paid).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        return new StaffPaymentCalculationDto(
                staff.getId(),
                staff.getEmail(),
                displayNameOf(staff),
                method,
                rate,
                jobCount,
                units,
                expected,
                paid,
                unpaid,
                staff.getStaffTargetPeriod(),
                staff.getStaffTargetValue(),
                staff.getStaffBonusPercentage());
    }

    private BigDecimal unitsForSingleOrder(StaffRateUnit method, Order order, List<Order> allMine) {
        if (method == null) {
            return BigDecimal.ZERO;
        }
        return switch (method) {
            case PER_SERVICE, HOURLY -> BigDecimal.ONE;
            case DAILY, WEEKLY, MONTHLY -> BigDecimal.ONE;
        };
    }

    private BigDecimal lineAmount(StaffRateUnit method, BigDecimal rate, BigDecimal units, Order order) {
        if (method == null || rate == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (method == StaffRateUnit.MONTHLY) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return units.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private List<Order> completedOrdersForProvider(Long providerId, LocalDate startDate, LocalDate endDate) {
        List<Order> all = orderRepository.findByProvider_IdAndStatusIn(providerId, List.of(OrderStatus.COLLECTED));
        if (startDate == null && endDate == null) {
            return all;
        }
        return all.stream()
                .filter(o -> {
                    LocalDate d = completionDate(o);
                    if (startDate != null && d.isBefore(startDate)) {
                        return false;
                    }
                    if (endDate != null && d.isAfter(endDate)) {
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    private LocalDate completionDate(Order o) {
        Instant at = o.getCompletedAt() != null ? o.getCompletedAt() : o.getCreatedAt();
        return at.atZone(ZONE).toLocalDate();
    }

    private UserAccount requireStaff(Long providerId, Long staffUserId) {
        return userAccountRepository
                .findByIdAndProvider_Id(staffUserId, providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STAFF", "User not found in this organization"));
    }

    private Provider loadProvider(Long id) {
        return providerRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
    }

    private void applyProfileFields(UserAccount u, String firstName, String lastName, String phone) {
        if (firstName != null) {
            u.setFirstName(firstName.trim());
        }
        if (lastName != null) {
            u.setLastName(lastName.trim());
        }
        if (phone != null) {
            u.setPhoneNumber(phone.trim());
        }
        String dn = displayNameOf(u);
        if (dn != null && !dn.isBlank()) {
            u.setDisplayName(dn);
        }
    }

    private String displayNameOf(UserAccount u) {
        String fn = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String ln = u.getLastName() == null ? "" : u.getLastName().trim();
        String combined = (fn + " " + ln).trim();
        if (!combined.isEmpty()) {
            return combined;
        }
        return u.getDisplayName();
    }

    private String normalizeTargetPeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        String p = period.trim().toUpperCase(Locale.ROOT);
        if (p.equals("DAILY") || p.equals("WEEKLY") || p.equals("MONTHLY")) {
            return p;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "TARGET_PERIOD", "Target period must be DAILY, WEEKLY, or MONTHLY");
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
                u.getFirstName(),
                u.getLastName(),
                u.getPhoneNumber(),
                displayNameOf(u),
                u.getRole(),
                u.isEnabled(),
                owner,
                u.getStaffRateUnit(),
                u.getStaffCompensationRate(),
                u.getStaffTargetPeriod(),
                u.getStaffTargetValue(),
                u.getStaffBonusPercentage(),
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
        Set<ProviderPermissionKey> applicable = providerPermissionService.applicableKeys(provider.getSubtype());
        EnumSet<ProviderPermissionKey> filtered = EnumSet.noneOf(ProviderPermissionKey.class);
        for (ProviderPermissionKey k : keys) {
            if (applicable.contains(k)) {
                filtered.add(k);
            }
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
