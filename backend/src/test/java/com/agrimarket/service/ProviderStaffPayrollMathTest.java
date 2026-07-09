package com.agrimarket.service;

import com.agrimarket.api.dto.StaffIncomeLineDto;
import com.agrimarket.api.dto.StaffPaymentCalculationDto;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.OrderStatus;
import com.agrimarket.domain.StaffPayrollJobMark;
import com.agrimarket.domain.StaffRateUnit;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.ProviderStaffPermissionRepository;
import com.agrimarket.repo.StaffPayrollJobMarkRepository;
import com.agrimarket.repo.UserAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderStaffPayrollMathTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    @Mock
    UserAccountRepository userAccountRepository;
    @Mock
    ProviderRepository providerRepository;
    @Mock
    ProviderStaffPermissionRepository providerStaffPermissionRepository;
    @Mock
    ProviderPermissionService providerPermissionService;
    @Mock
    StaffPayrollJobMarkRepository staffPayrollJobMarkRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    ProviderStaffService service;

    UserAccount staff;

    @BeforeEach
    void setUp() {
        staff = new UserAccount();
        staff.setId(10L);
        staff.setEmail("staff@example.com");
        staff.setRole(UserRole.PROVIDER_STAFF);
        staff.setEnabled(true);
        staff.setStaffCompensationRate(new BigDecimal("100.00"));
    }

    @Test
    void perDay_onlyFirstOrderOfDayGetsRate() {
        staff.setStaffRateUnit(StaffRateUnit.PER_DAY);
        Instant day1 = LocalDate.of(2026, 7, 1).atStartOfDay(ZONE).toInstant();
        Instant day1Later = LocalDate.of(2026, 7, 1).atTime(15, 0).atZone(ZONE).toInstant();
        Instant day2 = LocalDate.of(2026, 7, 2).atStartOfDay(ZONE).toInstant();

        Order a = order(1L, day1);
        Order b = order(2L, day1Later);
        Order c = order(3L, day2);

        when(staffPayrollJobMarkRepository.findByStaff_IdAndOrder_IdIn(eq(10L), anyCollection()))
                .thenReturn(List.of());

        StaffPaymentCalculationDto calc = service.calculateForStaff(staff, List.of(a, b, c));
        assertEquals(0, new BigDecimal("200.00").compareTo(calc.unpaidPayment()));
        assertEquals(0, new BigDecimal("2").compareTo(calc.units()));
        assertEquals(3, calc.jobCount());
    }

    @Test
    void monthly_pendingIsFullRateWhenAnyUnpaid() {
        staff.setStaffRateUnit(StaffRateUnit.MONTHLY);
        Instant t = LocalDate.of(2026, 7, 5).atStartOfDay(ZONE).toInstant();
        Order a = order(1L, t);
        Order b = order(2L, t.plusSeconds(3600));

        when(staffPayrollJobMarkRepository.findByStaff_IdAndOrder_IdIn(eq(10L), anyCollection()))
                .thenReturn(List.of());

        StaffPaymentCalculationDto calc = service.calculateForStaff(staff, List.of(a, b));
        assertEquals(0, new BigDecimal("100.00").compareTo(calc.unpaidPayment()));
        assertEquals(0, BigDecimal.ZERO.compareTo(calc.units()));
    }

    @Test
    void weekly_pendingClearsWhenAllMarked() {
        staff.setStaffRateUnit(StaffRateUnit.WEEKLY);
        Instant t = LocalDate.of(2026, 7, 5).atStartOfDay(ZONE).toInstant();
        Order a = order(1L, t);

        StaffPayrollJobMark mark = new StaffPayrollJobMark();
        mark.setOrder(a);
        mark.setIncludeBonus(false);

        when(staffPayrollJobMarkRepository.findByStaff_IdAndOrder_IdIn(eq(10L), anyCollection()))
                .thenReturn(List.of(mark));

        StaffPaymentCalculationDto calc = service.calculateForStaff(staff, List.of(a));
        assertEquals(0, BigDecimal.ZERO.compareTo(calc.unpaidPayment()));
        assertEquals(0, new BigDecimal("100.00").compareTo(calc.paidPayment()));
    }

    @Test
    void bonus_notInUnpaidTotals_butAppliedOnPaidWhenIncludeBonus() {
        staff.setStaffRateUnit(StaffRateUnit.PER_SERVICE);
        staff.setStaffBonusPercentage(new BigDecimal("10"));
        Instant t = LocalDate.of(2026, 7, 5).atStartOfDay(ZONE).toInstant();
        Order a = order(1L, t);

        when(staffPayrollJobMarkRepository.findByStaff_IdAndOrder_IdIn(eq(10L), anyCollection()))
                .thenReturn(List.of());

        StaffPaymentCalculationDto unpaid = service.calculateForStaff(staff, List.of(a));
        assertEquals(0, new BigDecimal("100.00").compareTo(unpaid.unpaidPayment()), "bonus not in pending");

        StaffPayrollJobMark mark = new StaffPayrollJobMark();
        mark.setOrder(a);
        mark.setIncludeBonus(true);
        when(staffPayrollJobMarkRepository.findByStaff_IdAndOrder_IdIn(eq(10L), anyCollection()))
                .thenReturn(List.of(mark));

        StaffPaymentCalculationDto paid = service.calculateForStaff(staff, List.of(a));
        assertEquals(0, new BigDecimal("110.00").compareTo(paid.paidPayment()));
        assertEquals(0, BigDecimal.ZERO.compareTo(paid.unpaidPayment()));
    }

    private Order order(Long id, Instant completedAt) {
        Order o = new Order();
        o.setId(id);
        o.setStatus(OrderStatus.COLLECTED);
        o.setCompletedAt(completedAt);
        o.setCreatedAt(completedAt);
        o.setCompletedByStaffId(10L);
        o.setGuestName("Guest");
        o.setTotalAmount(new BigDecimal("50.00"));
        return o;
    }
}
