package com.agrimarket.domain;

import java.util.EnumSet;
import java.util.Set;
import java.util.Arrays;

/** Provider-side permission keys for staff/admin users (owner implicitly has all). */
public enum ProviderPermissionKey {
    // Listings
    LISTINGS_READ(ProviderSubtype.RESELLER, ProviderSubtype.RENTING_OWNER),
    LISTINGS_WRITE(ProviderSubtype.RESELLER, ProviderSubtype.RENTING_OWNER),

    // Staff & payroll
    TEAM_READ(ProviderSubtype.RESELLER, ProviderSubtype.RENTING_OWNER),
    TEAM_MANAGE(ProviderSubtype.RESELLER, ProviderSubtype.RENTING_OWNER),
    PAYROLL_READ(ProviderSubtype.RESELLER, ProviderSubtype.RENTING_OWNER),
    PAYROLL_WRITE(ProviderSubtype.RESELLER, ProviderSubtype.RENTING_OWNER),

    // Orders / bookings (future-facing; keeps UI filtering stable)
    ORDERS_READ(ProviderSubtype.RESELLER),
    ORDERS_WRITE(ProviderSubtype.RESELLER),
    RENTALS_READ(ProviderSubtype.RENTING_OWNER),
    RENTALS_WRITE(ProviderSubtype.RENTING_OWNER);

    private final EnumSet<ProviderSubtype> applicableTo;

    ProviderPermissionKey(ProviderSubtype... subtypes) {
        if (subtypes.length == 0) {
            this.applicableTo = EnumSet.noneOf(ProviderSubtype.class);
        } else if (subtypes.length == 1) {
            this.applicableTo = EnumSet.of(subtypes[0]);
        } else {
            this.applicableTo = EnumSet.of(subtypes[0], Arrays.copyOfRange(subtypes, 1, subtypes.length));
        }
    }

    public Set<ProviderSubtype> applicableTo() {
        return applicableTo;
    }
}

