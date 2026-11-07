/**
 * Referral invite helpers.
 *
 * An invite link looks like `/register?ref=CODE`. The code is remembered in localStorage for 30 days
 * (so closing the tab doesn't lose it) and pre-fills the sign-up form. Because the code is also a
 * visible, editable field on the form and is sent with the registration request, it survives
 * cross-device sign-up: someone who got the link on their phone can type the code on a laptop.
 */

export const INVITE_REF_STORAGE_KEY = 'agri_referral_ref';
export const INVITE_REF_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_CODE_LENGTH = 32;

/** Upper-case, strip spaces and dashes. Returns '' for anything that can't be a code. */
export function normalizeReferralCode(raw) {
  if (raw == null) return '';
  const code = String(raw).replace(/[\s-]+/g, '').toUpperCase();
  if (!code || code.length > MAX_CODE_LENGTH || !/^[A-Z0-9]+$/.test(code)) return '';
  return code;
}

export function persistInviteRef(raw, now = Date.now()) {
  const ref = normalizeReferralCode(raw);
  if (!ref) return '';
  try {
    localStorage.setItem(INVITE_REF_STORAGE_KEY, JSON.stringify({ ref, at: now }));
  } catch {
    // storage unavailable (private mode) — the form field still carries the code
  }
  return ref;
}

export function readInviteRef(now = Date.now()) {
  try {
    const raw = localStorage.getItem(INVITE_REF_STORAGE_KEY);
    if (!raw) return '';
    const parsed = JSON.parse(raw);
    const ref = normalizeReferralCode(parsed?.ref);
    if (!ref || typeof parsed?.at !== 'number' || now - parsed.at > INVITE_REF_TTL_MS) {
      localStorage.removeItem(INVITE_REF_STORAGE_KEY);
      return '';
    }
    return ref;
  } catch {
    return '';
  }
}

export function clearInviteRef() {
  try {
    localStorage.removeItem(INVITE_REF_STORAGE_KEY);
  } catch {
    // ignore
  }
}

/** Remember `?ref=` from any route query; returns the stored code or ''. */
export function captureInviteFromQuery(query, now = Date.now()) {
  const value = Array.isArray(query?.ref) ? query.ref[0] : query?.ref;
  return value ? persistInviteRef(value, now) : '';
}

export function buildInviteLink(origin, code) {
  const base = String(origin || '').replace(/\/+$/, '');
  return `${base}/register?ref=${encodeURIComponent(code || '')}`;
}

export function formatPercent(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 'Not set';
  return `${Number.isInteger(n) ? n : n.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')}%`;
}

export function formatZar(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 'Not set';
  return `R ${n.toFixed(2)}`;
}

const STATUS_LABELS = {
  PENDING: 'Signed up, waiting for first payment',
  EARNED: 'Commission earned, payout pending',
  PAID: 'Commission paid out',
};

export function referralStatusLabel(status) {
  return STATUS_LABELS[status] || status || 'Not set';
}

/** Admin list query params from the filter form (drops blanks). */
export function buildAdminReferralParams(filters = {}, page = 0, size = 20) {
  const params = { page, size };
  for (const key of ['referrer', 'referee', 'status', 'from', 'to']) {
    const v = filters[key];
    if (v != null && String(v).trim() !== '') params[key] = String(v).trim();
  }
  return params;
}
