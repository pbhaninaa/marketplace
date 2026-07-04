package com.agrimarket.domain;

/** Rate basis for provider staff compensation (aligned with Wheel Hub pay methods). */
public enum StaffRateUnit {
    /** Pay per completed order/collection. */
    PER_SERVICE,
    /** Pay per hour (1 hour credited per completed order when duration is unknown). */
    HOURLY,
    /** Pay per distinct calendar day with completed work. */
    DAILY,
    /** Pay per distinct calendar week with completed work. */
    WEEKLY,
    /** Flat monthly salary (rate is the full period amount). */
    MONTHLY
}

