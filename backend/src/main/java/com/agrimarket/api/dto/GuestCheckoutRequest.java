package com.agrimarket.api.dto;

import com.agrimarket.domain.PaymentMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GuestCheckoutRequest(
        @NotBlank String guestName,
        @NotBlank @Email String guestEmail,
        @NotBlank String guestPhone,
        @NotBlank String deliveryOrPickup,
        @NotNull PaymentMethod paymentMethod) {}
