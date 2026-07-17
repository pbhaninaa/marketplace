import { describe, expect, it } from 'vitest';
import {
  normalizeAcceptedPaymentMethods,
  peachPaymentPayload,
} from '../src/utils/paymentModel';

describe('WheelHub payment model', () => {
  it('normalizes historical manual EFT settings to Peach', () => {
    expect(normalizeAcceptedPaymentMethods(['EFT'])).toEqual(['PEACH']);
    expect(normalizeAcceptedPaymentMethods(['BOTH'])).toEqual(['CASH', 'PEACH']);
  });

  it('keeps only current top-level payment methods', () => {
    expect(normalizeAcceptedPaymentMethods(['CASH', 'PEACH'])).toEqual(['CASH', 'PEACH']);
  });

  it('sends a subtype only for Peach', () => {
    expect(peachPaymentPayload('PEACH', 'EFT')).toEqual({ peachPaymentMethod: 'EFT' });
    expect(peachPaymentPayload('CASH', 'CARD')).toEqual({});
  });
});
