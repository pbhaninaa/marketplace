import { describe, expect, it } from 'vitest';
import {
  formatRand,
  payoutReferenceError,
  settlementLabel,
  settlementTone,
  sumSelected,
} from '../src/utils/payouts';
import { ONLINE_PAYMENT_LABEL, gatewayPageName } from '../src/utils/paymentModel';

describe('payout helpers', () => {
  it('labels every settlement status in plain words', () => {
    expect(settlementLabel('HELD')).toBe('Awaiting payout');
    expect(settlementLabel('PAID_OUT')).toBe('Paid out');
    expect(settlementLabel('REFUND_DUE')).toBe('Refund due to buyer');
    expect(settlementLabel('REFUNDED')).toBe('Refunded to buyer');
    expect(settlementLabel('SOMETHING')).toBe('SOMETHING');
    for (const s of ['NONE', 'HELD', 'PAID_OUT', 'REFUND_DUE', 'REFUNDED']) {
      expect(settlementLabel(s)).not.toMatch(/[–—]/);
    }
  });

  it('picks a tone per status', () => {
    expect(settlementTone('HELD')).toBe('warn');
    expect(settlementTone('PAID_OUT')).toBe('ok');
    expect(settlementTone('REFUND_DUE')).toBe('danger');
    expect(settlementTone('NONE')).toBe('muted');
  });

  it('sums only the selected rows without floating point drift', () => {
    const rows = [
      { paymentId: 1, sellerPayoutAmount: 0.1, amount: 1 },
      { paymentId: 2, sellerPayoutAmount: 0.2, amount: 2 },
      { paymentId: 3, sellerPayoutAmount: '92.50', amount: 100 },
    ];
    expect(sumSelected(rows, [1, 2])).toBe(0.3);
    expect(sumSelected(rows, [3])).toBe(92.5);
    expect(sumSelected(rows, [1, 3], 'amount')).toBe(101);
    expect(sumSelected(rows, [])).toBe(0);
  });

  it('formats rand amounts', () => {
    expect(formatRand(1234.5)).toBe('R 1 234.50');
    expect(formatRand('0')).toBe('R 0.00');
    expect(formatRand(-12)).toBe('-R 12.00');
    expect(formatRand(undefined)).toBe('R 0.00');
  });

  it('requires a payout reference', () => {
    expect(payoutReferenceError('  ')).toBeTruthy();
    expect(payoutReferenceError('x'.repeat(121))).toBeTruthy();
    expect(payoutReferenceError(' FNB-123 ')).toBe('');
  });

  it('names the online option and the gateway page', () => {
    expect(ONLINE_PAYMENT_LABEL).toBe('Card / Instant EFT');
    expect(gatewayPageName('PAYFAST')).toBe('PayFast');
    expect(gatewayPageName('PEACH')).toBe('Peach Payments');
    expect(gatewayPageName('')).toBe('payment');
  });
});
