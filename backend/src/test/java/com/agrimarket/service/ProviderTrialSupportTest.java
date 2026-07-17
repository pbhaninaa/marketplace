package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ProviderTrialSupportTest {

    @Test
    void assignTrialOnce_setsThirtyDayWindowAndDoesNotReset() {
        var provider = new com.agrimarket.domain.Provider("T", "t", "d", "l");
        Instant start = Instant.parse("2026-01-15T12:00:00Z");
        ProviderTrialSupport.assignTrialOnce(provider, start);

        assertThat(provider.getTrialStartedAt()).isEqualTo(start);
        assertThat(provider.getTrialEndsAt()).isEqualTo(start.plus(30, ChronoUnit.DAYS));

        ProviderTrialSupport.assignTrialOnce(provider, Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(provider.getTrialStartedAt()).isEqualTo(start);
        assertThat(provider.getTrialEndsAt()).isEqualTo(start.plus(30, ChronoUnit.DAYS));
    }

    @Test
    void assignTrialOnce_skipsWhenOnlyEndAlreadySet() {
        var provider = new com.agrimarket.domain.Provider("T", "t", "d", "l");
        Instant existingEnd = Instant.parse("2026-02-01T00:00:00Z");
        provider.setTrialEndsAt(existingEnd);

        ProviderTrialSupport.assignTrialOnce(provider, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(provider.getTrialStartedAt()).isNull();
        assertThat(provider.getTrialEndsAt()).isEqualTo(existingEnd);
    }

    @Test
    void isOnTrial_usesStrictInstantAfterBoundaryUtc() {
        Instant ends = Instant.parse("2026-03-01T00:00:00Z");
        assertThat(ProviderTrialSupport.isOnTrial(ends.minusSeconds(1), ends)).isTrue();
        assertThat(ProviderTrialSupport.isOnTrial(ends, ends)).isFalse();
        assertThat(ProviderTrialSupport.isOnTrial(ends.plusSeconds(1), ends)).isFalse();
    }

    @Test
    void trialDaysRemaining_usesUtcCalendarDatesAtMidnightBoundary() {
        Instant ends = Instant.parse("2026-07-17T00:00:00Z");
        // Same UTC calendar day as end → 0 days remaining, but still active until the Instant.
        Instant justBeforeEnd = Instant.parse("2026-07-16T23:59:59Z");
        assertThat(ProviderTrialSupport.isOnTrial(justBeforeEnd, ends)).isTrue();
        assertThat(ProviderTrialSupport.trialDaysRemaining(justBeforeEnd, ends)).isEqualTo(1);

        Instant onEndDayBeforeExpiry = Instant.parse("2026-07-17T00:00:00Z").minusSeconds(1);
        // still 16 Jul UTC
        assertThat(LocalDate.ofInstant(onEndDayBeforeExpiry, ZoneOffset.UTC)).isEqualTo(LocalDate.parse("2026-07-16"));
        assertThat(ProviderTrialSupport.trialDaysRemaining(onEndDayBeforeExpiry, ends)).isEqualTo(1);

        Instant midEndDayWouldBeIfEndsLater = Instant.parse("2026-07-10T12:00:00Z");
        Instant endsLater = Instant.parse("2026-07-17T12:00:00Z");
        assertThat(ProviderTrialSupport.trialDaysRemaining(midEndDayWouldBeIfEndsLater, endsLater)).isEqualTo(7);

        // Exactly at expiry: not on trial, 0 days.
        assertThat(ProviderTrialSupport.trialDaysRemaining(ends, ends)).isEqualTo(0);

        // Just after UTC midnight into the end calendar day while Instant still before a later end.
        Instant afterUtcMidnight = Instant.parse("2026-07-17T00:00:01Z");
        Instant endsEvening = Instant.parse("2026-07-17T18:00:00Z");
        assertThat(ProviderTrialSupport.isOnTrial(afterUtcMidnight, endsEvening)).isTrue();
        assertThat(ProviderTrialSupport.trialDaysRemaining(afterUtcMidnight, endsEvening)).isEqualTo(0);
    }

    @Test
    void trialDaysRemaining_fullWindowFromRegistrationAnchor() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = start.plus(30, ChronoUnit.DAYS);
        assertThat(end).isEqualTo(Instant.parse("2026-01-31T00:00:00Z"));
        assertThat(ProviderTrialSupport.trialDaysRemaining(start, end)).isEqualTo(30);
        assertThat(ProviderTrialSupport.trialDaysRemaining(start.plus(29, ChronoUnit.DAYS), end)).isEqualTo(1);
    }
}
