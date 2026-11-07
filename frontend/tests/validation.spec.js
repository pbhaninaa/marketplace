import { describe, it, expect } from 'vitest';
import {
  isValidEmail,
  isPositiveNumber,
  isMinInt,
  isNonEmptyString,
  normalizePhone,
  isValidPhone,
  isValidSAPhoneNumber,
  isValidEmailOrPhone,
  isValidPersonName,
  isValidBusinessName,
  isValidBankAccountNumber,
  isValidBranchCode,
  isValidMediaUrls,
  digitsOnly,
  emailError,
  phoneError,
  personNameError,
  businessNameError,
  bankAccountError,
  branchCodeError,
  textError,
  numberError,
  mediaUrlsError,
  getFieldErrorMessage,
} from '../src/utils/validation';

describe('validation utils', () => {
  it('isValidEmail', () => {
    expect(isValidEmail('a@b.com')).toBe(true);
    expect(isValidEmail('guest+abc@checkout.test')).toBe(true);
    expect(isValidEmail(' first.last@sub.example.co.za ')).toBe(true);
    expect(isValidEmail('not-an-email')).toBe(false);
    expect(isValidEmail('a@b')).toBe(false);
    expect(isValidEmail('two@@example.com')).toBe(false);
    expect(isValidEmail('dots..here@example.com')).toBe(false);
    expect(isValidEmail('space in@example.com')).toBe(false);
    expect(isValidEmail('')).toBe(false);
  });

  it('isPositiveNumber', () => {
    expect(isPositiveNumber('1')).toBe(true);
    expect(isPositiveNumber(0)).toBe(false);
    expect(isPositiveNumber('0.0')).toBe(false);
    expect(isPositiveNumber('')).toBe(false);
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

describe('phones: country code + 9 digits (same as API)', () => {
  it('accepts and normalises international and local shapes', () => {
    expect(normalizePhone('+27 82 123 4567')).toBe('+27821234567');
    expect(normalizePhone('+27821234567')).toBe('+27821234567');
    expect(normalizePhone('0027821234567')).toBe('+27821234567');
    expect(normalizePhone('082 123 4567')).toBe('+27821234567');
    expect(normalizePhone('0821234567')).toBe('+27821234567');
    expect(normalizePhone('821234567')).toBe('+27821234567');
    expect(normalizePhone('+27 (0)82-123-4567')).toBe('+27821234567');
    expect(normalizePhone('+263 77 123 4567')).toBe('+263771234567');
  });

  it('rejects wrong lengths, letters and unsupported countries', () => {
    expect(normalizePhone('082123456')).toBeNull();
    expect(normalizePhone('08212345678')).toBeNull();
    expect(normalizePhone('+44 7911 123456')).toBeNull();
    expect(normalizePhone('082 ABC 4567')).toBeNull();
    expect(normalizePhone('')).toBeNull();
    expect(isValidPhone('12345')).toBe(false);
  });

  it('isValidSAPhoneNumber only accepts +27 numbers', () => {
    expect(isValidSAPhoneNumber('0721234567')).toBe(true);
    expect(isValidSAPhoneNumber('+263771234567')).toBe(false);
  });

  it('email-or-phone targets', () => {
    expect(isValidEmailOrPhone('client@example.test')).toBe(true);
    expect(isValidEmailOrPhone('0821234567')).toBe(true);
    expect(isValidEmailOrPhone('nope')).toBe(false);
  });
});

describe('names', () => {
  it('person names', () => {
    expect(isValidPersonName('Thandi')).toBe(true);
    expect(isValidPersonName("Jean-Luc O'Neil")).toBe(true);
    expect(isValidPersonName('Zoë  van der Merwe')).toBe(true);
    expect(isValidPersonName('R2D2')).toBe(false);
    expect(isValidPersonName('<script>')).toBe(false);
    expect(isValidPersonName('-Leading')).toBe(false);
    expect(isValidPersonName('a'.repeat(101))).toBe(false);
  });

  it('business names', () => {
    expect(isValidBusinessName('Test Farm Co')).toBe(true);
    expect(isValidBusinessName('Smith & Sons (Pty) Ltd.')).toBe(true);
    expect(isValidBusinessName('4x4 Agri/Feeds')).toBe(true);
    expect(isValidBusinessName('<b>Farm</b>')).toBe(false);
    expect(isValidBusinessName('Farm; DROP TABLE')).toBe(false);
  });
});

describe('bank details and links', () => {
  it('account numbers and branch codes', () => {
    expect(isValidBankAccountNumber('1234567890')).toBe(true);
    expect(isValidBankAccountNumber('1234 5678-90')).toBe(true);
    expect(isValidBankAccountNumber('12345')).toBe(false);
    expect(isValidBankAccountNumber('12345678901234567')).toBe(false);
    expect(isValidBankAccountNumber('12345abc90')).toBe(false);
    expect(digitsOnly('1234 5678-90')).toBe('1234567890');

    expect(isValidBranchCode('250655')).toBe(true);
    expect(isValidBranchCode('250 655')).toBe(true);
    expect(isValidBranchCode('2506')).toBe(false);
    expect(isValidBranchCode('25O655')).toBe(false);
  });

  it('listing image links', () => {
    expect(isValidMediaUrls('/api/public/images/1')).toBe(true);
    expect(isValidMediaUrls('https://cdn.example.com/a.jpg, /api/public/images/2')).toBe(true);
    expect(isValidMediaUrls('javascript:alert(1)')).toBe(false);
    expect(isValidMediaUrls('//evil.example.com/a.png')).toBe(false);
    expect(isValidMediaUrls('https://ok.example.com/a b.png')).toBe(false);
  });
});

describe('error-message helpers', () => {
  it('blank only fails when required', () => {
    expect(emailError('')).toBe('Email is required');
    expect(emailError('', { required: false })).toBe('');
    expect(phoneError('  ', { required: false })).toBe('');
    expect(bankAccountError('')).toBe('');
    expect(branchCodeError('')).toBe('');
    expect(mediaUrlsError('')).toBe('');
  });

  it('format messages match the API wording', () => {
    expect(emailError('x@y')).toMatch(/valid email/);
    expect(phoneError('123')).toMatch(/country code plus 9 digits/);
    expect(personNameError('R2D2')).toMatch(/letters/);
    expect(businessNameError('<b>', { label: 'Bank name' })).toMatch(/^Bank name must start/);
    expect(bankAccountError('12')).toBe('Account number must be 6 to 16 digits');
    expect(branchCodeError('12')).toBe('Branch code must be 6 digits');
    expect(mediaUrlsError('ftp://x')).toMatch(/Image links/);
    expect(emailError('name@example.com')).toBe('');
    expect(phoneError('+27 82 123 4567')).toBe('');
  });

  it('textError checks trimmed length', () => {
    expect(textError('a', { label: 'Title', min: 2, max: 5 })).toBe('Title must be at least 2 characters');
    expect(textError('abcdef', { label: 'Title', max: 5 })).toBe('Title must be at most 5 characters');
    expect(textError('  abc  ', { max: 5 })).toBe('');
    expect(textError('', { required: true, label: 'Location' })).toBe('Location is required');
  });

  it('numberError checks ranges', () => {
    expect(numberError('0', { label: 'Price', min: 0, exclusiveMin: true })).toBe('Price must be greater than 0');
    expect(numberError('-1', { label: 'Stock', min: 0 })).toBe('Stock must be at least 0');
    expect(numberError('1.5', { label: 'Stock', integer: true })).toBe('Stock must be a whole number');
    expect(numberError('101', { label: 'Bonus %', max: 100 })).toBe('Bonus % must be at most 100');
    expect(numberError('abc', { label: 'Price' })).toBe('Price must be a number');
    expect(numberError('', { label: 'Price' })).toBe('');
    expect(numberError(12.5, { min: 0, exclusiveMin: true, max: 1000 })).toBe('');
  });

  it('getFieldErrorMessage keeps working for existing callers', () => {
    expect(getFieldErrorMessage('email', '')).toBe('Email is required');
    expect(getFieldErrorMessage('phone', '0821234567')).toBeNull();
    expect(getFieldErrorMessage('name', 'Thandi')).toBeNull();
  });
});
