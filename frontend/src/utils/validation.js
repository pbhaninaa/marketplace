/**
 * Form validation rules. These mirror the API's checks in
 * backend/src/main/java/com/agrimarket/util/InputValidator.java (and the DTO annotations) — keep
 * the two in step. The API is the real guard; these give instant feedback in the forms.
 *
 * `isValid*` return booleans (blank = invalid). The `*Error` helpers return '' when fine or a
 * message to show under the field; blank values only fail when `required` is set.
 */

export function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

const isBlank = (v) => v === null || v === undefined || String(v).trim() === '';

// ---------- Limits shared with the API ----------
export const LIMITS = {
  email: 254,
  personName: 100,
  businessName: 200,
  bankName: 200,
  location: 255,
  providerDescription: 2000,
  listingTitle: 200,
  listingDescription: 4000,
  categoryName: 200,
  bankReference: 140,
  deliveryAddress: 255,
  invoiceAddress: 2000,
  invoiceFooterNote: 2000,
  invoiceVatNumber: 80,
  systemName: 120,
  referenceHint: 200,
  maxPrice: 10000000,
  maxStock: 1000000,
  maxCartQuantity: 10000,
  maxDeliveryKm: 1000,
  maxPricePerKm: 10000,
};

// ---------- Email ----------
const EMAIL_RE =
  /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\.[A-Za-z]{2,24}$/;

/** One @, no spaces, a dot in the domain, a 2+ letter ending, 64 before @ and 254 overall. */
export function isValidEmail(value) {
  if (!isNonEmptyString(value)) return false;
  const e = value.trim();
  return e.length <= LIMITS.email && EMAIL_RE.test(e) && !e.includes('..');
}

// ---------- Phone: country code + 9 digits ----------
/** Supported dialling codes and their national number length. */
export const NATIONAL_LENGTH_BY_CODE = { 27: 9, 263: 9 };
const DEFAULT_CODE = '27';

/**
 * E.164 (`+27821234567`) or null. Accepts `+27821234567`, `+27 82 123 4567`, `0027…`,
 * `082 123 4567` and `821234567` (local shapes are read as South African).
 */
export function normalizePhone(value) {
  if (isBlank(value)) return null;
  const s = String(value).trim().replace(/[\s\-().]/g, '');
  if (!/^\+?\d+$/.test(s)) return null;
  if (s.startsWith('+') || s.startsWith('00')) {
    const digits = s.startsWith('+') ? s.slice(1) : s.slice(2);
    for (const [code, len] of Object.entries(NATIONAL_LENGTH_BY_CODE)) {
      if (digits.startsWith(code)) {
        let national = digits.slice(code.length);
        if (national.startsWith('0')) national = national.slice(1);
        return national.length === len ? `+${code}${national}` : null;
      }
    }
    return null;
  }
  const national = s.startsWith('0') ? s.slice(1) : s;
  return national.length === NATIONAL_LENGTH_BY_CODE[DEFAULT_CODE] ? `+${DEFAULT_CODE}${national}` : null;
}

export const isValidPhone = (value) => normalizePhone(value) !== null;

/** South African number (+27 plus 9 digits, or local 0XX XXX XXXX). */
export function isValidSAPhoneNumber(value) {
  const n = normalizePhone(value);
  return n !== null && n.startsWith('+27');
}

export const isValidEmailOrPhone = (value) => isValidEmail(value) || isValidPhone(value);

// ---------- Names ----------
const PERSON_NAME_RE = /^\p{L}[\p{L}\p{M} .'’-]{0,99}$/u;
const BUSINESS_NAME_RE = /^[\p{L}\p{N}][\p{L}\p{M}\p{N} &'’.,()/+-]*$/u;

const squash = (v) => String(v).trim().replace(/\s+/g, ' ');

export const isValidPersonName = (value) => !isBlank(value) && PERSON_NAME_RE.test(squash(value));
export const isValidBusinessName = (value) => !isBlank(value) && BUSINESS_NAME_RE.test(squash(value));

// ---------- Bank details ----------
export const isValidBankAccountNumber = (value) =>
  !isBlank(value) && /^\d{6,16}$/.test(String(value).replace(/[\s-]/g, ''));

/** South African branch codes are 6 digits. */
export const isValidBranchCode = (value) => !isBlank(value) && /^\d{6}$/.test(String(value).replace(/\s/g, ''));

/** Account / branch number with spaces and dashes removed. */
export const digitsOnly = (value) => (isBlank(value) ? '' : String(value).replace(/[\s-]/g, ''));

// ---------- Links ----------
/** Comma-separated http(s) links or server paths ("/..."), no whitespace, ≤ 500 chars each. */
export function isValidMediaUrls(value) {
  if (isBlank(value)) return false;
  return String(value)
    .split(',')
    .map((p) => p.trim())
    .filter(Boolean)
    .every((p) => {
      if (p.length > 500 || /\s/.test(p)) return false;
      return (p.startsWith('/') && !p.startsWith('//')) || /^https?:\/\/[^/\s]+(\/.*)?$/i.test(p);
    });
}

// ---------- Numbers ----------
export function toNumberOrNull(value) {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

export function isPositiveNumber(value) {
  const n = toNumberOrNull(value);
  return n != null && n > 0;
}

export function isMinInt(value, min) {
  const n = Number(value);
  return Number.isFinite(n) && Number.isInteger(n) && n >= min;
}

// ---------- Error-message helpers (same wording as the API where possible) ----------
function requiredOr(value, required, label) {
  if (isBlank(value)) return required ? `${label} is required` : '';
  return null;
}

export function emailError(value, { required = true, label = 'Email' } = {}) {
  const r = requiredOr(value, required, label);
  if (r !== null) return r;
  return isValidEmail(value) ? '' : 'Enter a valid email address, e.g. name@example.com';
}

export function phoneError(value, { required = true, label = 'Phone number' } = {}) {
  const r = requiredOr(value, required, label);
  if (r !== null) return r;
  return isValidPhone(value) ? '' : 'Enter the country code plus 9 digits, e.g. +27 82 123 4567 or 082 123 4567';
}

export function emailOrPhoneError(value, { required = true, label = 'Email or phone' } = {}) {
  const r = requiredOr(value, required, label);
  if (r !== null) return r;
  return isValidEmailOrPhone(value) ? '' : 'Enter a valid email address or a phone number like +27 82 123 4567';
}

export function personNameError(value, { required = true, label = 'Name' } = {}) {
  const r = requiredOr(value, required, label);
  if (r !== null) return r;
  return isValidPersonName(value) ? '' : 'Use letters, spaces, apostrophes, dots or hyphens only (up to 100 characters)';
}

export function businessNameError(value, { required = true, label = 'Name', max = LIMITS.businessName } = {}) {
  const r = requiredOr(value, required, label);
  if (r !== null) return r;
  if (String(value).trim().length > max) return `${label} must be at most ${max} characters`;
  return isValidBusinessName(value)
    ? ''
    : `${label} must start with a letter or number and use only letters, numbers, spaces and & ' . , - ( ) / +`;
}

export function bankAccountError(value, { required = false, label = 'Account number' } = {}) {
  const r = requiredOr(value, required, label);
  if (r !== null) return r;
  return isValidBankAccountNumber(value) ? '' : `${label} must be 6 to 16 digits`;
}

export function branchCodeError(value, { required = false, label = 'Branch code' } = {}) {
  const r = requiredOr(value, required, label);
  if (r !== null) return r;
  return isValidBranchCode(value) ? '' : `${label} must be 6 digits`;
}

export function mediaUrlsError(value) {
  if (isBlank(value)) return '';
  return isValidMediaUrls(value) ? '' : 'Image links must be https://… or /api/… paths, separated by commas';
}

/** Free text length check (trimmed). */
export function textError(value, { required = false, label = 'This field', min = 0, max } = {}) {
  const r = requiredOr(value, required, label);
  if (r !== null) return r;
  const len = String(value).trim().length;
  if (min && len < min) return `${label} must be at least ${min} characters`;
  if (max && len > max) return `${label} must be at most ${max} characters`;
  return '';
}

/**
 * Number range check. `exclusiveMin` means the value must be greater than `min` (e.g. prices > 0).
 */
export function numberError(
  value,
  { required = false, label = 'Value', min, max, exclusiveMin = false, integer = false } = {}
) {
  const r = requiredOr(value, required, label);
  if (r !== null) return r;
  const n = toNumberOrNull(value);
  if (n === null) return `${label} must be a number`;
  if (integer && !Number.isInteger(n)) return `${label} must be a whole number`;
  if (min !== undefined) {
    if (exclusiveMin && n <= min) return `${label} must be greater than ${min}`;
    if (!exclusiveMin && n < min) return `${label} must be at least ${min}`;
  }
  if (max !== undefined && n > max) return `${label} must be at most ${max}`;
  return '';
}

/** First non-empty message, or ''. */
export const firstError = (...messages) => messages.find((m) => !!m) || '';

/**
 * Get validation error message for a field (kept for existing callers).
 */
export function getFieldErrorMessage(fieldName, value, isRequired = true, customValidator = null) {
  if (customValidator) return customValidator(value, isRequired);
  const byField = {
    name: () => personNameError(value, { required: isRequired }) || null,
    email: () => emailError(value, { required: isRequired }) || null,
    phone: () => phoneError(value, { required: isRequired }) || null,
  };
  const validator = byField[fieldName];
  return validator ? validator() : null;
}
