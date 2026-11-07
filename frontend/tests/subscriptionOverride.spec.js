import { describe, expect, it } from 'vitest';
import {
  toIsoDate,
  validateActiveUntil,
  validateExtendDays,
  validateReason,
} from '../src/utils/subscriptionOverride';

describe('subscription override validation', () => {
  it('requires a non-blank reason within the limit', () => {
    expect(validateReason('')).toMatch(/required/);
    expect(validateReason('   ')).toMatch(/required/);
    expect(validateReason('x'.repeat(501))).toMatch(/500/);
    expect(validateReason('Pilot partner')).toBe('');
  });

  it('accepts 1 to 365 whole days only', () => {
    expect(validateExtendDays(0)).not.toBe('');
    expect(validateExtendDays(366)).not.toBe('');
    expect(validateExtendDays(2.5)).not.toBe('');
    expect(validateExtendDays('abc')).not.toBe('');
    expect(validateExtendDays(1)).toBe('');
    expect(validateExtendDays('30')).toBe('');
    expect(validateExtendDays(365)).toBe('');
  });

  it('allows an empty or future active-until date only', () => {
    const today = new Date(2026, 10, 8);
    expect(validateActiveUntil('', today)).toBe('');
    expect(validateActiveUntil('2026-11-08', today)).toBe('');
    expect(validateActiveUntil('2027-01-31', today)).toBe('');
    expect(validateActiveUntil('2026-11-07', today)).toMatch(/today or later/);
    expect(validateActiveUntil('31/12/2026', today)).toMatch(/valid date/);
    expect(toIsoDate(today)).toBe('2026-11-08');
  });
});
