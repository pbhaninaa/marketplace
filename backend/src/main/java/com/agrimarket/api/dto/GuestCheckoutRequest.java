package com.agrimarket.api.dto;

import com.agrimarket.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record GuestCheckoutRequest(
        @NotBlank String guestName,
        @NotBlank @Email String guestEmail,
        @NotBlank String guestPhone,
        @NotBlank String deliveryOrPickup,
        @NotNull PaymentMethod paymentMethod,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal deliveryDistanceKm,
        String deliveryAddress,
        String latitude,
        String longitude
) {}
