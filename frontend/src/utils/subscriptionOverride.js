// Client-side checks for platform-owner subscription overrides (server re-validates everything).

export const MAX_EXTEND_DAYS = 365;
export const MAX_REASON_LENGTH = 500;

export const OVERRIDE_PLAN_OPTIONS = [
  { value: 'BASIC', label: 'Basic (R199 / month)' },
  { value: 'PREMIUM', label: 'Premium (R499 / month)' },
];

export const OVERRIDE_ACTION_LABELS = {
  EXTEND_TRIAL: 'Trial extended',
  END_TRIAL: 'Trial ended',
  SET_SUBSCRIPTION: 'Subscription set',
};

export function validateReason(reason) {
  const r = String(reason || '').trim();
  if (!r) return 'A reason is required.';
  if (r.length > MAX_REASON_LENGTH) return `Reason must be at most ${MAX_REASON_LENGTH} characters.`;
  return '';
}

export function validateExtendDays(days) {
  const n = Number(days);
  if (!Number.isInteger(n) || n < 1 || n > MAX_EXTEND_DAYS) {
    return `Enter a whole number of days from 1 to ${MAX_EXTEND_DAYS}.`;
  }
  return '';
}

/** `activeUntil` is optional (YYYY-MM-DD); when set it must be today or later. */
export function validateActiveUntil(activeUntil, today = new Date()) {
  if (!activeUntil) return '';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(activeUntil)) return 'Use a valid date.';
  const todayIso = toIsoDate(today);
  if (activeUntil < todayIso) return 'Active-until date must be today or later.';
  return '';
}

export function toIsoDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
