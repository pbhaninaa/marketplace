package com.agrimarket.api.dto;

import java.time.LocalDate;
import java.util.List;

public record StaffPaymentCalculationsBundleDto(
        LocalDate payPeriodStart, LocalDate payPeriodEnd, List<StaffPaymentCalculationDto> calculations) {}
