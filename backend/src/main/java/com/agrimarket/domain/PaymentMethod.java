package com.agrimarket.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.EnumSet;
import java.util.Set;

/**
 * Top-level client payment methods. New checkout/settings flows expose only {@link #CASH} and
 * {@link #PEACH}. {@link #EFT} and {@link #BOTH} remain readable for historical database records.
 */
public enum PaymentMethod {
    CASH,
    /** Legacy manual bank transfer; never selectable for a new checkout. */
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
        return EnumSet.of(CASH, PEACH);
    }

    /**
     * Converts legacy provider settings to the current model. Historical EFT becomes Peach online
     * payment, while BOTH becomes Cash + Peach; the legacy enum values themselves remain readable.
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
                    out.add(PEACH);
                } else if (m == EFT) {
                    out.add(PEACH);
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
        return this == CASH || this == PEACH;
    }

    public String clientLabel() {
        return switch (this) {
            case CASH -> "Cash on collection / delivery";
            case EFT -> "Legacy manual EFT";
            case PEACH -> "Pay online (card / instant EFT)";
            case BOTH -> "Legacy Cash + manual EFT";
        };
    }

    public String providerLabel() {
        return switch (this) {
            case CASH -> "Cash";
            case EFT -> "Legacy manual EFT";
            case PEACH -> "Pay online (Peach)";
            case BOTH -> "Legacy Cash + manual EFT";
        };
    }
}
