export function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

/**
 * Validates email format using a more comprehensive regex pattern
 */
export function isValidEmail(value) {
  if (!isNonEmptyString(value)) return false;
  const email = value.trim().toLowerCase();
  // More comprehensive email validation
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) return false;
  // Additional checks
  if (email.length > 254) return false; // RFC 5321
  const [localPart, domain] = email.split('@');
  if (localPart.length > 64) return false; // RFC 5321
  if (domain.length < 4) return false; // Must have at least example.co
  return true;
}

/**
 * Validates South African phone numbers
 * Formats:
 * - 27XXXXXXXXXX (with country code)
 * - +27XXXXXXXXXX (with country code and +)
 * - 0XXXXXXXXXX (local format with leading 0)
 * - (XX) XXXX XXXX (formatted)
 * - XX XXXX XXXX (spaced)
 */
export function isValidSAPhoneNumber(value) {
  if (!isNonEmptyString(value)) return false;
  const phone = value.trim().replace(/[\s()\-]/g, ''); // Remove formatting
  
  // Must be numeric after removing formatting
  if (!/^[\d+]+$/.test(phone)) return false;
  
  // Remove + if present
  let cleaned = phone.startsWith('+') ? phone.substring(1) : phone;
  
  // Convert 0 prefix to 27 (SA country code)
  if (cleaned.startsWith('0')) {
    cleaned = '27' + cleaned.substring(1);
  }
  
  // Must be 11 digits (27 + 9 digit number)
  if (cleaned.length !== 11) return false;
  
  // Must start with 27 (country code)
  if (!cleaned.startsWith('27')) return false;
  
  // Third and fourth digits must be valid SA operator codes (0-8)
  const operatorCode = cleaned.substring(2, 4);
  if (!/^[0-8]\d$/.test(operatorCode)) return false;
  
  return true;
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

/**
 * Get validation error message for a field
 */
export function getFieldErrorMessage(fieldName, value, isRequired = true, customValidator = null) {
  const messages = {
    'name': () => !isNonEmptyString(value) && isRequired ? 'Name is required' : null,
    'email': () => {
      if (!value && isRequired) return 'Email is required';
      if (value && !isValidEmail(value)) return 'Please enter a valid email address (e.g., name@example.com)';
      return null;
    },
    'phone': () => {
      if (!value && isRequired) return 'Phone number is required';
      if (value && !isValidSAPhoneNumber(value)) return 'Please enter a valid South African phone number (e.g., 0721234567 or +27721234567)';
      return null;
    },
  };
  
  if (customValidator) return customValidator(value, isRequired);
  const validator = messages[fieldName];
  return validator ? validator() : null;
}

