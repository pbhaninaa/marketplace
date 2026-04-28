package com.agrimarket.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentMethod {
    CASH,
    EFT,
    BOTH

    ;

    @JsonCreator
    public static PaymentMethod fromJson(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "CASH" -> CASH;
            case "EFT" -> EFT;
            case "BOTH" -> BOTH;
            default -> throw new IllegalArgumentException("Unknown payment method: " + raw);
        };
    }
}
