import { describe, it, expect } from 'vitest';
import { apiErrorMessage, apiErrorCode } from '../src/utils/apiError';

function httpError(status, data, headers = {}) {
  return { message: `Request failed with status code ${status}`, response: { status, data, headers } };
}

describe('apiErrorMessage', () => {
  it('shows the server message for a locked account', () => {
    const e = httpError(
      429,
      { code: 'ACCOUNT_LOCKED', message: 'Too many failed sign-in attempts. Try again in 15 minutes or reset your password.' },
      { 'retry-after': '900' },
    );
    expect(apiErrorMessage(e, 'Login failed')).toContain('Too many failed sign-in attempts');
    expect(apiErrorCode(e)).toBe('ACCOUNT_LOCKED');
  });

  it('builds a wait message from Retry-After when a 429 has no body', () => {
    expect(apiErrorMessage(httpError(429, '', { 'retry-after': '61' }))).toBe(
      'Too many attempts. Please wait 2 minutes and try again.',
    );
    expect(apiErrorMessage(httpError(429, null))).toBe('Too many attempts. Please wait a few minutes and try again.');
  });

  it('uses the server message for 401 and password policy errors', () => {
    expect(apiErrorMessage(httpError(401, { code: 'AUTH', message: 'Invalid email or password' }))).toBe(
      'Invalid email or password',
    );
    expect(
      apiErrorMessage(httpError(400, { code: 'WEAK_PASSWORD', message: 'This password is too common. Choose something harder to guess' })),
    ).toContain('too common');
  });

  it('falls back sensibly', () => {
    expect(apiErrorMessage(httpError(500, {}), 'Could not save')).toBe('Could not save');
    expect(apiErrorMessage(new Error('Network Error'))).toBe('Network error. Check your connection.');
    expect(apiErrorMessage(new Error('Enter a valid email'))).toBe('Enter a valid email');
    expect(apiErrorMessage({})).toBe('Network error. Check your connection.');
  });
});
