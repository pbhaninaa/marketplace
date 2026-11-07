import { afterEach, describe, expect, it } from 'vitest';
import {
  INVITE_REF_STORAGE_KEY,
  INVITE_REF_TTL_MS,
  buildAdminReferralParams,
  buildInviteLink,
  captureInviteFromQuery,
  clearInviteRef,
  formatPercent,
  normalizeReferralCode,
  persistInviteRef,
  readInviteRef,
} from '../src/utils/referralInvite';

describe('referral invite memory', () => {
  afterEach(() => clearInviteRef());

  it('normalises codes typed by hand', () => {
    expect(normalizeReferralCode(' green-abc12 ')).toBe('GREENABC12');
    expect(normalizeReferralCode('')).toBe('');
    expect(normalizeReferralCode('bad code!')).toBe('');
    expect(normalizeReferralCode('X'.repeat(33))).toBe('');
  });

  it('remembers ?ref= in localStorage', () => {
    expect(captureInviteFromQuery({ ref: 'abc123' }, 1000)).toBe('ABC123');
    expect(JSON.parse(localStorage.getItem(INVITE_REF_STORAGE_KEY))).toEqual({ ref: 'ABC123', at: 1000 });
    expect(readInviteRef(2000)).toBe('ABC123');
    expect(captureInviteFromQuery({}, 3000)).toBe('');
    expect(readInviteRef(3000)).toBe('ABC123');
  });

  it('expires after 30 days', () => {
    persistInviteRef('abc123', 0);
    expect(readInviteRef(INVITE_REF_TTL_MS - 1)).toBe('ABC123');
    expect(readInviteRef(INVITE_REF_TTL_MS + 1)).toBe('');
    expect(localStorage.getItem(INVITE_REF_STORAGE_KEY)).toBeNull();
  });

  it('builds a shareable link', () => {
    expect(buildInviteLink('https://agri.example.com/', 'ABC123')).toBe('https://agri.example.com/register?ref=ABC123');
  });

  it('formats percent and admin filter params', () => {
    expect(formatPercent(15)).toBe('15%');
    expect(formatPercent('12.50')).toBe('12.5%');
    expect(buildAdminReferralParams({ referrer: ' alice ', referee: '', status: 'EARNED', from: '2026-01-01' }, 2, 50)).toEqual({
      page: 2,
      size: 50,
      referrer: 'alice',
      status: 'EARNED',
      from: '2026-01-01',
    });
  });
});
