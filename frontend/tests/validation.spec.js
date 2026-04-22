import { describe, it, expect } from 'vitest';
import { isValidEmail, isPositiveNumber, isMinInt, isNonEmptyString } from '../src/utils/validation';

describe('validation utils', () => {
  it('isValidEmail', () => {
    expect(isValidEmail('a@b.com')).toBe(true);
    expect(isValidEmail('not-an-email')).toBe(false);
    expect(isValidEmail('')).toBe(false);
  });

  it('isPositiveNumber', () => {
    expect(isPositiveNumber('1')).toBe(true);
    expect(isPositiveNumber(0)).toBe(false);
    expect(isPositiveNumber('0.0')).toBe(false);
  });

  it('isMinInt', () => {
    expect(isMinInt(1, 1)).toBe(true);
    expect(isMinInt(0, 1)).toBe(false);
    expect(isMinInt(1.2, 1)).toBe(false);
  });

  it('isNonEmptyString', () => {
    expect(isNonEmptyString('x')).toBe(true);
    expect(isNonEmptyString('  ')).toBe(false);
  });
});

