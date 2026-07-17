import { describe, expect, it } from 'vitest';
import {
  normalizeAcceptedPaymentMethods,
  peachPaymentPayload,
} from '../src/utils/paymentModel';

describe('payment model', () => {
  it('keeps manual EFT and expands legacy BOTH to Cash + Manual EFT', () => {
    expect(normalizeAcceptedPaymentMethods(['EFT'])).toEqual(['EFT']);
    expect(normalizeAcceptedPaymentMethods(['BOTH'])).toEqual(['CASH', 'EFT']);
  });

  it('keeps current top-level payment methods', () => {
    expect(normalizeAcceptedPaymentMethods(['CASH', 'EFT', 'PEACH'])).toEqual([
      'CASH',
      'EFT',
      'PEACH',
    ]);
  });

  it('sends a subtype only for Peach', () => {
    expect(peachPaymentPayload('PEACH', 'EFT')).toEqual({ peachPaymentMethod: 'EFT' });
    expect(peachPaymentPayload('CASH', 'CARD')).toEqual({});
    expect(peachPaymentPayload('EFT', 'CARD')).toEqual({});
  });
});
