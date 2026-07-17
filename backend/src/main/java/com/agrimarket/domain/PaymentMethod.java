package com.agrimarket.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.EnumSet;
import java.util.Set;

/**
 * Top-level client payment methods for cart checkout. Selectable values are {@link #CASH},
 * {@link #EFT} (manual bank transfer), and {@link #PEACH} (online Hosted Checkout). {@link #BOTH}
 * remains readable for historical database records and expands to Cash + Manual EFT.
 */
public enum PaymentMethod {
    CASH,
    /** Manual bank transfer to the provider. */
    EFT,
    /** Online card / instant EFT via Peach Payments Hosted Checkout (platform merchant account). */
    PEACH,
    /** Legacy combined Cash/manual-EFT setting. */
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
            case "PEACH" -> PEACH;
            case "BOTH" -> BOTH;
            // Legacy clients that still send "CARD" get routed to the Peach online checkout.
            case "CARD" -> PEACH;
            default -> throw new IllegalArgumentException("Unknown payment method: " + raw);
        };
    }

    public static Set<PaymentMethod> defaultAccepted() {
        return EnumSet.of(CASH, EFT, PEACH);
    }

    /**
     * Expands legacy {@link #BOTH} to Cash + Manual EFT. Leaves CASH, EFT, and PEACH unchanged.
     */
    public static Set<PaymentMethod> normalizeAccepted(Set<PaymentMethod> raw) {
        EnumSet<PaymentMethod> out = EnumSet.noneOf(PaymentMethod.class);
        if (raw != null) {
            for (PaymentMethod m : raw) {
                if (m == null) {
                    continue;
                }
                if (m == BOTH) {
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
        return this == CASH || this == EFT || this == PEACH;
    }

    public String clientLabel() {
        return switch (this) {
            case CASH -> "Cash on collection / delivery";
            case EFT -> "EFT (bank transfer)";
            case PEACH -> "Pay online (card / instant EFT)";
            case BOTH -> "Legacy Cash + manual EFT";
        };
    }

    public String providerLabel() {
        return switch (this) {
            case CASH -> "Cash";
            case EFT -> "Manual EFT";
            case PEACH -> "Pay online (Peach)";
            case BOTH -> "Legacy Cash + manual EFT";
        };
    }
}
