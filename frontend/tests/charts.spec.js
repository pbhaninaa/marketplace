import { describe, expect, it } from 'vitest';
import {
  formatBucketLabel,
  formatShare,
  formatTick,
  niceScale,
  tickIndices,
} from '../src/components/charts/chartUtils';
import {
  orderStatusParts,
  paymentMethodItems,
  revenueSeries,
  topItems,
  perBucket,
} from '../src/components/charts/dashboardSeries';

describe('chart utils', () => {
  it('rounds axes up to clean steps', () => {
    expect(niceScale(437).ticks).toEqual([0, 100, 200, 300, 400, 500]);
    expect(niceScale(9).max).toBeGreaterThanOrEqual(9);
    expect(niceScale(0).ticks[0]).toBe(0);
  });

  it('keeps ticks in full under 10,000 and compact above', () => {
    // Full number under 10K (thousands separator depends on the en-ZA locale data).
    expect(formatTick(2000, 4000)).toMatch(/^2\D?000$/);
    expect(formatTick(20000, 40000, { money: true })).toBe('R20K');
    expect(formatTick(1500000, 2000000)).toBe('1.5M');
  });

  it('labels buckets without dashes', () => {
    expect(formatBucketLabel('2026-03-02')).toBe('2 Mar');
    expect(formatBucketLabel('2026-03-02', 'week', { long: true })).toBe('Week of 2 Mar 2026');
    expect(formatBucketLabel('2026-03-02', 'day', { long: true })).not.toContain('-');
  });

  it('spreads x ticks and always keeps the ends', () => {
    const idx = tickIndices(31, 6);
    expect(idx[0]).toBe(0);
    expect(idx[idx.length - 1]).toBe(30);
    expect(idx.length).toBeLessThanOrEqual(6);
  });

  it('formats shares', () => {
    expect(formatShare(1, 3)).toBe('33%');
    expect(formatShare(1, 1000)).toBe('<1%');
    expect(formatShare(0, 0)).toBe('0%');
  });
});

describe('dashboard series mapping', () => {
  const trends = {
    granularity: 'week',
    buckets: [
      { start: '2026-03-02', orders: 2, ordersRevenue: '100.50', rentals: 1, rentalsRevenue: '40' },
      { start: '2026-03-09', orders: 0, ordersRevenue: 0, rentals: 0, rentalsRevenue: 0 },
    ],
    orderStatus: [
      { key: 'PENDING_PAYMENT', label: 'Awaiting payment', count: 1 },
      { key: 'CANCELLED', label: 'Cancelled', count: 2 },
    ],
    paymentMethods: [{ key: 'CASH', label: 'Cash', count: 1, amount: '99.00' }],
    topItems: [{ key: 'provider-1', label: 'Big Farm', count: 0, amount: '900' }],
  };

  it('maps revenue to numbers with fixed colour slots', () => {
    const [sales, rentals] = revenueSeries(trends);
    expect(sales.values).toEqual([100.5, 0]);
    expect(rentals.values).toEqual([40, 0]);
    expect([sales.slot, rentals.slot]).toEqual([0, 1]);
  });

  it('keeps a part colour by key, not by position', () => {
    const parts = orderStatusParts(trends);
    expect(parts.find((p) => p.key === 'CANCELLED').slot).toBe(3);
  });

  it('maps payments and top items', () => {
    expect(paymentMethodItems(trends)[0]).toMatchObject({ value: 99, detail: '1 payment' });
    expect(topItems(trends, { money: true })[0]).toMatchObject({ label: 'Big Farm', value: 900 });
    expect(perBucket(trends)).toBe('per week');
  });
});

describe('sign-ups', () => {
  it('shows a running total so growth reads as a rising line', async () => {
    const { signupSeries } = await import('../src/components/charts/dashboardSeries');
    const [users, providers] = signupSeries({
      buckets: [
        { start: '2026-03-02', newUsers: 2, newProviders: 1 },
        { start: '2026-03-03', newUsers: 0, newProviders: 0 },
        { start: '2026-03-04', newUsers: 3, newProviders: 1 },
      ],
    });
    expect(users.values).toEqual([2, 2, 5]);
    expect(providers.values).toEqual([1, 1, 2]);
  });
});
