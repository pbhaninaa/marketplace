package com.agrimarket.domain;

/** Rate basis for provider staff compensation (Wheel Hub pay method names). */
public enum StaffRateUnit {
    /** Pay per completed order/collection. */
    PER_SERVICE,
    /** Pay per hour (1 hour credited per completed order when duration is unknown). */
    PER_HOUR,
    /** Pay per distinct calendar day with completed work. */
    PER_DAY,
    /** Flat weekly salary (rate is the full period amount). */
    WEEKLY,
    /** Flat monthly salary (rate is the full period amount). */
    MONTHLY;

    public boolean isFixedPeriod() {
        return this == WEEKLY || this == MONTHLY;
    }
}
