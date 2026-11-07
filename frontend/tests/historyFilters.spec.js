import { describe, expect, it } from 'vitest';
import {
  buildHistoryParams,
  countActiveFilters,
  emptyHistoryFilters,
  formatMoney,
  resolveDateRange,
} from '../src/utils/historyFilters';

const NOW = new Date(2026, 10, 8, 14, 30); // 8 Nov 2026 (local)

describe('resolveDateRange', () => {
  it('resolves presets to inclusive local dates', () => {
    expect(resolveDateRange('TODAY', '', '', NOW)).toEqual({ from: '2026-11-08', to: '2026-11-08' });
    expect(resolveDateRange('LAST_7', '', '', NOW)).toEqual({ from: '2026-11-02', to: '2026-11-08' });
    expect(resolveDateRange('LAST_30', '', '', NOW)).toEqual({ from: '2026-10-10', to: '2026-11-08' });
    expect(resolveDateRange('THIS_MONTH', '', '', NOW)).toEqual({ from: '2026-11-01', to: '2026-11-08' });
    expect(resolveDateRange('ALL', '', '', NOW)).toEqual({});
  });

  it('accepts open-ended and swaps reversed custom ranges', () => {
    expect(resolveDateRange('CUSTOM', '2026-01-05', '', NOW)).toEqual({ from: '2026-01-05' });
    expect(resolveDateRange('CUSTOM', '2026-03-01', '2026-02-01', NOW)).toEqual({ from: '2026-02-01', to: '2026-03-01' });
  });
});

describe('buildHistoryParams', () => {
  const filters = {
    ...emptyHistoryFilters(),
    status: 'PAID',
    paymentStatus: 'PAID',
    method: 'EFT',
    providerId: '12',
    kind: 'RENTAL',
    q: '  jane ',
    preset: 'TODAY',
  };

  it('keeps only the filters that apply to the orders tab', () => {
    expect(buildHistoryParams(filters, { tab: 'orders', now: NOW })).toEqual({
      status: 'PAID',
      paymentStatus: 'PAID',
      method: 'EFT',
      q: 'jane',
      from: '2026-11-08',
      to: '2026-11-08',
    });
  });

  it('sends the payment type on the payments tab and the provider only for admins', () => {
    expect(buildHistoryParams(filters, { tab: 'payments', allowProvider: true, now: NOW })).toEqual({
      paymentStatus: 'PAID',
      method: 'EFT',
      kind: 'RENTAL',
      providerId: '12',
      q: 'jane',
      from: '2026-11-08',
      to: '2026-11-08',
    });
  });

  it('counts active filters and is empty by default', () => {
    expect(buildHistoryParams(emptyHistoryFilters())).toEqual({});
    expect(countActiveFilters(emptyHistoryFilters())).toBe(0);
    expect(countActiveFilters({ ...emptyHistoryFilters(), method: 'CASH' })).toBe(1);
  });
});

describe('formatMoney', () => {
  it('formats rand amounts', () => {
    expect(formatMoney(1234.5)).toMatch(/^R 1.?234[.,]50$/);
    expect(formatMoney(null)).toMatch(/^R 0[.,]00$/);
  });
});
