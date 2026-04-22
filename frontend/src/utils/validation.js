export function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

export function isValidEmail(value) {
  if (!isNonEmptyString(value)) return false;
  // pragmatic email check; backend does authoritative validation
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

export function toNumberOrNull(value) {
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

