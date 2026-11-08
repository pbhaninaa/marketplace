package com.agrimarket.api.dto;

import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PeachPaymentMethod;
import com.agrimarket.util.validation.PersonName;
import com.agrimarket.util.validation.ValidEmail;
import com.agrimarket.util.validation.ValidPhone;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record GuestCheckoutRequest(
        @NotBlank @Size(max = 100) @PersonName String guestName,
        @NotBlank @Size(max = 254) @ValidEmail String guestEmail,
        @NotBlank @ValidPhone String guestPhone,
        @NotBlank @Size(max = 1000) String deliveryOrPickup,
        @NotNull PaymentMethod paymentMethod,
        PeachPaymentMethod peachPaymentMethod,
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1000.0") BigDecimal deliveryDistanceKm,
        @Size(max = 255) String deliveryAddress,
        @Pattern(regexp = "^$|^-?\\d{1,3}(\\.\\d{1,20})?$", message = "must be a decimal coordinate") String latitude,
        @Pattern(regexp = "^$|^-?\\d{1,3}(\\.\\d{1,20})?$", message = "must be a decimal coordinate") String longitude,
        /**
         * Which seller's items to check out. Optional when the cart holds one seller; required when it
         * holds several (each seller is checked out separately, the rest stay in the cart).
         */
        Long providerId
) {}
