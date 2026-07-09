package com.agrimarket.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.EnumSet;
import java.util.Set;

/**
 * Client payment methods at checkout (Wheel Hub: CASH + EFT).
 * {@link #BOTH} is legacy storage only — expand via {@link #normalizeAccepted}.
 */
public enum PaymentMethod {
    CASH,
    EFT,
    /** @deprecated Prefer storing CASH and EFT as separate accepted methods. */
    BOTH;

    @JsonCreator
    public static PaymentMethod fromJson(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "CASH" -> CASH;
            case "EFT" -> EFT;
            case "BOTH" -> BOTH;
            case "CARD" -> throw new IllegalArgumentException(
                    "CARD is not supported. Choose CASH or EFT (Wheel Hub client payment methods).");
            default -> throw new IllegalArgumentException("Unknown payment method: " + raw);
        };
    }

    public static Set<PaymentMethod> defaultAccepted() {
        return EnumSet.of(CASH, EFT);
    }

    /** Expand legacy BOTH and drop it from the returned set; never empty. */
    public static Set<PaymentMethod> normalizeAccepted(Set<PaymentMethod> raw) {
        EnumSet<PaymentMethod> out = EnumSet.noneOf(PaymentMethod.class);
        if (raw != null) {
            for (PaymentMethod m : raw) {
                if (m == null || m == BOTH) {
                    out.add(CASH);
                    out.add(EFT);
                } else {
                    out.add(m);
                }
            }
        }
        if (out.isEmpty()) {
            return defaultAccepted();
        }
        return out;
    }

    public boolean isCheckoutSelectable() {
        return this == CASH || this == EFT;
    }

    public String clientLabel() {
        return switch (this) {
            case CASH -> "Cash on collection / delivery";
            case EFT -> "EFT (bank transfer)";
            case BOTH -> "EFT + Cash";
        };
    }

    public String providerLabel() {
        return switch (this) {
            case CASH -> "Cash";
            case EFT -> "EFT (bank transfer)";
            case BOTH -> "EFT + Cash";
        };
    }
}
