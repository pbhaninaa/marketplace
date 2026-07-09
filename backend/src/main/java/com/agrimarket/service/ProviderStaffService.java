package com.agrimarket.service;

import com.agrimarket.api.dto.CreateStaffRequest;
import com.agrimarket.api.dto.MarkStaffOrderPayrollRequest;
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
import com.agrimarket.domain.StaffPayrollJobMark;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.ProviderStaffPermissionRepository;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final StaffPayrollJobMarkRepository staffPayrollJobMarkRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    /** Defaults when owner creates staff and leaves permissions empty (Wheel Hub style). */
    public static EnumSet<ProviderPermissionKey> basePermissionsForNewStaff(Provider provider) {
        EnumSet<ProviderPermissionKey> keys = EnumSet.noneOf(ProviderPermissionKey.class);
        keys.add(ProviderPermissionKey.LISTINGS_READ);
        keys.add(ProviderPermissionKey.LISTINGS_WRITE);
        keys.add(ProviderPermissionKey.TEAM_READ);
        keys.add(ProviderPermissionKey.PAYROLL_READ);
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

    /** Hard-delete staff (Wheel Hub ManageTeam remove). Owner only. */
    @Transactional
    public void deleteStaff(MarketUserPrincipal actor, Long staffUserId) {
        UserAccount actorAccount = userAccountRepository
                .findById(actor.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Not authenticated"));
        if (actorAccount.getRole() != UserRole.PROVIDER_OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ONLY", "Only the provider owner can remove team members.");
        }
        UserAccount target = userAccountRepository
                .findByIdAndProvider_Id(staffUserId, actor.getProviderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STAFF", "User not found in this organization"));
        if (target.getRole() == UserRole.PROVIDER_OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_IMMUTABLE", "Provider owner cannot be deleted.");
        }
        if (target.getId().equals(actor.getUserId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_DELETE", "You cannot delete your own account.");
        }
        staffPayrollJobMarkRepository.deleteByStaff_Id(target.getId());
        if (target.getProvider() != null) {
            providerStaffPermissionRepository.deleteByProvider_IdAndUser_Id(target.getProvider().getId(), target.getId());
        }
        userAccountRepository.delete(target);
    }

    @Transactional(readOnly = true)
    public StaffPaymentCalculationsBundleDto listPaymentCalculations(
            MarketUserPrincipal actor, LocalDate startDate, LocalDate endDate) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_READ);
        requireBothOrNeitherDates(startDate, endDate);
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
        requireBothOrNeitherDates(startDate, endDate);
        UserAccount staff = requireStaff(actor.getProviderId(), staffUserId);
        if (staff.getRole() == UserRole.PROVIDER_OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_PAYROLL", "Owner has no staff income lines.");
        }
        return buildIncomeBundle(staff, actor.getProviderId(), startDate, endDate, false);
    }

    /** Staff self-view: expected salary from attributed collected orders (Wheel Hub my-expected-income). */
    @Transactional(readOnly = true)
    public StaffIncomeBundleDto myExpectedIncome(MarketUserPrincipal actor, LocalDate startDate, LocalDate endDate) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_READ);
        requireBothOrNeitherDates(startDate, endDate);
        UserAccount me = userAccountRepository
                .findById(actor.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER", "User not found"));
        if (me.getRole() == UserRole.PROVIDER_OWNER) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OWNER_INCOME",
                    "Owners use payment calculations for the team, not personal expected income.");
        }
        if (me.getProvider() == null || !me.getProvider().getId().equals(actor.getProviderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROVIDER", "Not a member of this provider");
        }
        return buildIncomeBundle(me, actor.getProviderId(), startDate, endDate, true);
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
        mark.setIncludeBonus(req.includeBonus() == null || Boolean.TRUE.equals(req.includeBonus()));
        staffPayrollJobMarkRepository.save(mark);
    }

    @Transactional
    public void unmarkOrderPayrollPaid(MarketUserPrincipal actor, Long staffUserId, Long orderId) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_WRITE);
        requireStaff(actor.getProviderId(), staffUserId);
        staffPayrollJobMarkRepository.deleteByStaff_IdAndOrder_Id(staffUserId, orderId);
    }

    @Transactional
    public int markAllUnpaidPayrollPaid(
            MarketUserPrincipal actor,
            Long staffUserId,
            LocalDate startDate,
            LocalDate endDate,
            Boolean includeBonus) {
        providerPermissionService.require(actor, ProviderPermissionKey.PAYROLL_WRITE);
        requireBothOrNeitherDates(startDate, endDate);
        UserAccount staff = requireStaff(actor.getProviderId(), staffUserId);
        List<Order> completed = completedOrdersForProvider(actor.getProviderId(), startDate, endDate).stream()
                .filter(o -> staff.getId().equals(o.getCompletedByStaffId()))
                .toList();
        Set<Long> paid = paidOrderIds(staff.getId(), completed);
        Provider p = loadProvider(actor.getProviderId());
        UserAccount marker = userAccountRepository.findById(actor.getUserId()).orElse(null);
        boolean bonus = includeBonus == null || Boolean.TRUE.equals(includeBonus);
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
            mark.setIncludeBonus(bonus);
            staffPayrollJobMarkRepository.save(mark);
            count++;
        }
        return count;
    }

    private StaffIncomeBundleDto buildIncomeBundle(
            UserAccount staff, Long providerId, LocalDate startDate, LocalDate endDate, boolean forStaffMember) {
        List<Order> allCompleted = completedOrdersForProvider(providerId, startDate, endDate);
        List<Order> mine = allCompleted.stream()
                .filter(o -> staff.getId().equals(o.getCompletedByStaffId()))
                .sorted(Comparator.comparing(this::completionInstant))
                .toList();
        StaffRateUnit method = staff.getStaffRateUnit();
        BigDecimal rate = nz(staff.getStaffCompensationRate());
        boolean fixed = method != null && method.isFixedPeriod();
        Map<Long, StaffPayrollJobMark> marks = marksByOrderId(staff.getId(), mine);
        List<StaffIncomeLineDto> lines = buildIncomeLines(staff, mine, marks);
        StaffPaymentCalculationDto calc = calculateForStaff(staff, allCompleted);

        String note = forStaffMember
                ? "These figures are your expected pay from completed orders attributed to you. "
                        + "Settlement is between you and your employer."
                : "Use “Pay” on each collected order after you settle payroll with your team member.";
        if (!forStaffMember && calc.unpaidTargetMetCount() != null && calc.unpaidTargetPeriods() != null) {
            note = note + " Target met " + calc.unpaidTargetMetCount() + " / " + calc.unpaidTargetPeriods()
                    + " " + (staff.getStaffTargetPeriod() != null ? staff.getStaffTargetPeriod() : "period")
                    + " bucket(s) for unpaid work.";
        }

        return new StaffIncomeBundleDto(
                staff.getId(),
                staff.getEmail(),
                displayNameOf(staff),
                method,
                rate,
                staff.getStaffBonusPercentage(),
                staff.getStaffTargetPeriod(),
                staff.getStaffTargetValue(),
                startDate,
                endDate,
                calc.expectedPayment().add(calc.paidPayment()),
                calc.paidPayment(),
                calc.unpaidPayment(),
                fixed,
                calc.unpaidTargetPeriods(),
                calc.unpaidTargetMetCount(),
                note,
                lines);
    }

    /**
     * Wheel Hub parity: expectedPayment / unpaidPayment = pending unpaid only;
     * bonus is NOT included in backend totals (applied at mark time via includeBonus).
     */
    StaffPaymentCalculationDto calculateForStaff(UserAccount staff, List<Order> allCompleted) {
        List<Order> mine = allCompleted.stream()
                .filter(o -> staff.getId().equals(o.getCompletedByStaffId()))
                .sorted(Comparator.comparing(this::completionInstant))
                .toList();
        StaffRateUnit method = staff.getStaffRateUnit();
        BigDecimal rate = nz(staff.getStaffCompensationRate());
        Map<Long, StaffPayrollJobMark> marks = marksByOrderId(staff.getId(), mine);
        List<StaffIncomeLineDto> lines = buildIncomeLines(staff, mine, marks);

        boolean fixed = method != null && method.isFixedPeriod();
        List<StaffIncomeLineDto> unpaidLines = lines.stream().filter(l -> !l.payrollPaid()).toList();
        List<StaffIncomeLineDto> paidLines = lines.stream().filter(StaffIncomeLineDto::payrollPaid).toList();

        BigDecimal unpaid;
        BigDecimal paid;
        BigDecimal units;
        long jobCount;

        if (fixed) {
            units = BigDecimal.ZERO;
            jobCount = unpaidLines.size();
            unpaid = unpaidLines.isEmpty() || rate.compareTo(BigDecimal.ZERO) <= 0
                    ? zero()
                    : rate.setScale(2, RoundingMode.HALF_UP);
            // Paid period salary once all lines marked (or no work)
            boolean allPaid = !mine.isEmpty() && unpaidLines.isEmpty();
            paid = allPaid && rate.compareTo(BigDecimal.ZERO) > 0
                    ? rate.setScale(2, RoundingMode.HALF_UP)
                    : zero();
            // Apply includeBonus on paid total when any mark requested bonus
            if (paid.compareTo(BigDecimal.ZERO) > 0 && anyIncludeBonus(marks, paidLines)) {
                paid = withBonus(paid, staff.getStaffBonusPercentage());
            }
            if (unpaid.compareTo(BigDecimal.ZERO) > 0) {
                // pending stays base rate (bonus only at mark/display time)
            }
        } else {
            unpaid = zero();
            paid = zero();
            for (StaffIncomeLineDto l : unpaidLines) {
                unpaid = unpaid.add(l.lineAmount());
            }
            for (StaffIncomeLineDto l : paidLines) {
                BigDecimal amt = l.lineAmount();
                StaffPayrollJobMark m = marks.get(l.orderId());
                if (m != null && Boolean.TRUE.equals(m.getIncludeBonus())) {
                    amt = withBonus(amt, staff.getStaffBonusPercentage());
                }
                paid = paid.add(amt);
            }
            unpaid = unpaid.setScale(2, RoundingMode.HALF_UP);
            paid = paid.setScale(2, RoundingMode.HALF_UP);
            units = calculateUnits(method, unpaidOrders(mine, marks));
            jobCount = unpaidLines.size();
        }

        TargetSummary targets = computeTargetSummary(staff, unpaidLines);

        return new StaffPaymentCalculationDto(
                staff.getId(),
                staff.getEmail(),
                displayNameOf(staff),
                method,
                rate,
                jobCount,
                units,
                unpaid,
                paid,
                unpaid,
                staff.getStaffTargetPeriod(),
                staff.getStaffTargetValue(),
                staff.getStaffBonusPercentage(),
                targets.periods(),
                targets.met());
    }

    private List<StaffIncomeLineDto> buildIncomeLines(
            UserAccount staff, List<Order> mine, Map<Long, StaffPayrollJobMark> marks) {
        StaffRateUnit method = staff.getStaffRateUnit();
        BigDecimal rate = nz(staff.getStaffCompensationRate());
        boolean fixed = method != null && method.isFixedPeriod();
        Set<LocalDate> seenDays = new HashSet<>();
        List<StaffIncomeLineDto> lines = new ArrayList<>();
        for (Order o : mine) {
            LocalDate day = completionDate(o);
            BigDecimal units;
            BigDecimal amount;
            String note = null;
            if (method == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                units = BigDecimal.ZERO;
                amount = zero();
            } else if (fixed) {
                units = BigDecimal.ZERO;
                amount = zero();
                note = "Period rate shown in summary";
            } else if (method == StaffRateUnit.PER_DAY) {
                if (seenDays.add(day)) {
                    units = BigDecimal.ONE;
                    amount = rate.setScale(2, RoundingMode.HALF_UP);
                } else {
                    units = BigDecimal.ZERO;
                    amount = zero();
                    note = "Day rate counted on first order of this date";
                }
            } else {
                // PER_SERVICE / PER_HOUR: 1 unit per order
                units = BigDecimal.ONE;
                amount = rate.setScale(2, RoundingMode.HALF_UP);
            }
            lines.add(new StaffIncomeLineDto(
                    o.getId(),
                    o.getGuestName(),
                    o.getTotalAmount(),
                    o.getCompletedAt() != null ? o.getCompletedAt() : o.getCreatedAt(),
                    units,
                    amount,
                    marks.containsKey(o.getId()),
                    note));
        }
        return lines;
    }

    private BigDecimal calculateUnits(StaffRateUnit method, List<Order> orders) {
        if (method == null || orders.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return switch (method) {
            case PER_SERVICE, PER_HOUR -> BigDecimal.valueOf(orders.size());
            case PER_DAY -> BigDecimal.valueOf(orders.stream().map(this::completionDate).collect(Collectors.toSet()).size());
            case WEEKLY, MONTHLY -> BigDecimal.ZERO;
        };
    }

    private List<Order> unpaidOrders(List<Order> mine, Map<Long, StaffPayrollJobMark> marks) {
        return mine.stream().filter(o -> !marks.containsKey(o.getId())).toList();
    }

    private TargetSummary computeTargetSummary(UserAccount staff, List<StaffIncomeLineDto> unpaidLines) {
        String targetPeriod = staff.getStaffTargetPeriod();
        BigDecimal targetValue = staff.getStaffTargetValue();
        if (targetPeriod == null || targetPeriod.isBlank() || targetValue == null || targetValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new TargetSummary(null, null);
        }
        String tp = targetPeriod.trim().toUpperCase(Locale.ROOT);
        WeekFields wf = WeekFields.ISO;
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        for (StaffIncomeLineDto l : unpaidLines) {
            if (l.completedAt() == null) {
                continue;
            }
            LocalDate d = l.completedAt().atZone(ZONE).toLocalDate();
            String key;
            if ("DAILY".equals(tp)) {
                key = d.toString();
            } else if ("WEEKLY".equals(tp)) {
                key = d.get(wf.weekBasedYear()) + "-W" + String.format(Locale.ROOT, "%02d", d.get(wf.weekOfWeekBasedYear()));
            } else if ("MONTHLY".equals(tp)) {
                key = d.getYear() + "-" + String.format(Locale.ROOT, "%02d", d.getMonthValue());
            } else {
                key = d.toString();
            }
            buckets.merge(key, l.lineAmount(), BigDecimal::add);
        }
        int met = 0;
        for (BigDecimal v : buckets.values()) {
            if (v.compareTo(targetValue) >= 0) {
                met++;
            }
        }
        return new TargetSummary(buckets.size(), met);
    }

    private record TargetSummary(Integer periods, Integer met) {}

    private Map<Long, StaffPayrollJobMark> marksByOrderId(Long staffId, List<Order> orders) {
        Map<Long, StaffPayrollJobMark> map = new HashMap<>();
        if (orders.isEmpty()) {
            return map;
        }
        for (StaffPayrollJobMark m : staffPayrollJobMarkRepository.findByStaff_IdAndOrder_IdIn(
                staffId, orders.stream().map(Order::getId).toList())) {
            map.put(m.getOrder().getId(), m);
        }
        return map;
    }

    private Set<Long> paidOrderIds(Long staffId, List<Order> orders) {
        return new HashSet<>(marksByOrderId(staffId, orders).keySet());
    }

    private boolean anyIncludeBonus(Map<Long, StaffPayrollJobMark> marks, List<StaffIncomeLineDto> paidLines) {
        for (StaffIncomeLineDto l : paidLines) {
            StaffPayrollJobMark m = marks.get(l.orderId());
            if (m != null && Boolean.TRUE.equals(m.getIncludeBonus())) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal withBonus(BigDecimal base, BigDecimal bonusPct) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0 || bonusPct == null || bonusPct.compareTo(BigDecimal.ZERO) <= 0) {
            return base == null ? zero() : base.setScale(2, RoundingMode.HALF_UP);
        }
        return base.multiply(BigDecimal.ONE.add(bonusPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void requireBothOrNeitherDates(LocalDate start, LocalDate end) {
        if ((start == null) != (end == null)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PAY_PERIOD",
                    "Provide both startDate and endDate for the pay period, or neither for all time");
        }
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
        return completionInstant(o).atZone(ZONE).toLocalDate();
    }

    private Instant completionInstant(Order o) {
        return o.getCompletedAt() != null ? o.getCompletedAt() : o.getCreatedAt();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
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
