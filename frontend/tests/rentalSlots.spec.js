import { describe, expect, it } from 'vitest';
import { findBookedOverlap, inclusiveDatesToRentalInstants, todayDateInput } from '../src/utils/rentalPricing';

/** A booked slot covering whole local days, the way the marketplace creates them. */
function slot(from, to) {
  const { rentalStart, rentalEnd } = inclusiveDatesToRentalInstants(from, to);
  return { start: rentalStart, end: rentalEnd };
}

describe('rental booked slots', () => {
  const booked = [slot('2026-12-01', '2026-12-03')];

  it('finds an overlap with any shared day', () => {
    expect(findBookedOverlap('2026-12-03', '2026-12-05', booked)).toBeTruthy();
    expect(findBookedOverlap('2026-11-28', '2026-12-01', booked)).toBeTruthy();
    expect(findBookedOverlap('2026-12-02', '2026-12-02', booked)).toBeTruthy();
  });

  it('allows the days right before and after (back-to-back)', () => {
    expect(findBookedOverlap('2026-12-04', '2026-12-06', booked)).toBeNull();
    expect(findBookedOverlap('2026-11-28', '2026-11-30', booked)).toBeNull();
  });

  it('ignores incomplete or reversed dates', () => {
    expect(findBookedOverlap('', '2026-12-02', booked)).toBeNull();
    expect(findBookedOverlap('2026-12-05', '2026-12-01', booked)).toBeNull();
  });

  it('formats today for the date picker minimum', () => {
    expect(todayDateInput(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
