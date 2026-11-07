import { describe, expect, it } from 'vitest';
import {
  formatCount,
  formatDate,
  formatDateRange,
  formatDateTime,
  formatEnum,
  formatMoney,
  imageCountLabel,
  orText,
} from '../src/utils/display';
import { formatRentalInclusiveRange } from '../src/utils/rentalPricing';

const DASHES = /[-–—]/;

describe('display helpers never render a dash', () => {
  it('orText uses a readable fallback for missing values', () => {
    expect(orText('Farm')).toBe('Farm');
    expect(orText(null)).toBe('Not set');
    expect(orText('  ', 'None')).toBe('None');
  });

  it('formatMoney formats rand amounts and zero for missing', () => {
    expect(formatMoney(1250)).toBe('R 1 250.00');
    expect(formatMoney(12.5)).toBe('R 12.50');
    expect(formatMoney(null)).toBe('R 0.00');
    expect(formatMoney(-30)).not.toMatch(DASHES);
    expect(formatMoney('abc', 'Nothing due')).toBe('Nothing due');
  });

  it('formatCount handles missing values', () => {
    expect(formatCount(0)).toBe('0');
    expect(formatCount(undefined)).toBe('0');
  });

  it('formats dates without ISO hyphens', () => {
    const d = formatDate('2026-11-07');
    expect(d).toContain('2026');
    expect(d).not.toMatch(DASHES);
    expect(formatDateTime('2026-11-07T14:05:00Z')).not.toMatch(DASHES);
    expect(formatDate('', 'Not set')).toBe('Not set');
  });

  it('formatDateRange joins with words, not a dash', () => {
    const r = formatDateRange('2026-10-08', '2026-11-07');
    expect(r).toContain(' to ');
    expect(r).not.toMatch(DASHES);
    expect(formatDateRange('2026-11-07', '2026-11-07')).not.toContain(' to ');
    expect(formatDateRange(null, null)).toBe('Not set');
  });

  it('formatEnum turns enum values into words', () => {
    expect(formatEnum('PREMIUM')).toBe('Premium');
    expect(formatEnum('PENDING_VERIFICATION')).toBe('Pending Verification');
    expect(formatEnum(null, 'No plan yet')).toBe('No plan yet');
  });

  it('imageCountLabel counts URLs instead of printing them', () => {
    expect(imageCountLabel('')).toBe('No images');
    expect(imageCountLabel('/a.jpg')).toBe('1 image');
    expect(imageCountLabel('/a.jpg, /b.jpg\n/c.jpg')).toBe('3 images');
  });

  it('rental ranges read "x to y"', () => {
    const r = formatRentalInclusiveRange('2026-06-01T00:00:00', '2026-06-04T00:00:00');
    expect(r).toContain(' to ');
    expect(r).not.toMatch(/[–—]/);
  });
});
